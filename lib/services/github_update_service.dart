import 'dart:convert';
import 'dart:io';
import 'package:flutter/services.dart';

sealed class UpdateResult {
  const UpdateResult();
}

class UpToDateResult extends UpdateResult {
  const UpToDateResult();
}

class NewVersionResult extends UpdateResult {
  final String version;
  final String downloadUrl;
  final String? changelog;

  const NewVersionResult({
    required this.version,
    required this.downloadUrl,
    this.changelog,
  });
}

class FutureVersionResult extends UpdateResult {
  const FutureVersionResult();
}

class ErrorResult extends UpdateResult {
  final String message;
  const ErrorResult([this.message = '']);
}

class GitHubUpdateService {
  static const MethodChannel _channel = MethodChannel('com.ixeken.wallpaper/media');
  static const String repositoryOwner = 'Ixeken-Studios';
  static const String repositoryName = 'gakuu-app';

  static Future<String> getCurrentAppVersion() async {
    return await _channel.invokeMethod<String>('getAppVersion') ?? '0.0.0';
  }
  static int compareVersions(String current, String latest) {
    final cleanCurrent = current.replaceFirst(RegExp(r'^[vV]'), '').trim();
    final cleanLatest = latest.replaceFirst(RegExp(r'^[vV]'), '').trim();
    if (cleanCurrent == cleanLatest) return 0;

    final currentSeparator = cleanCurrent.indexOf('-');
    final latestSeparator = cleanLatest.indexOf('-');
    final currentCore = currentSeparator < 0
        ? cleanCurrent
        : cleanCurrent.substring(0, currentSeparator);
    final latestCore = latestSeparator < 0
        ? cleanLatest
        : cleanLatest.substring(0, latestSeparator);
    final currentParts =
        currentCore.split('.').map((e) => int.tryParse(e) ?? 0).toList();
    final latestParts =
        latestCore.split('.').map((e) => int.tryParse(e) ?? 0).toList();

    final maxLength = currentParts.length > latestParts.length ? currentParts.length : latestParts.length;
    for (int i = 0; i < maxLength; i++) {
      final currVal = i < currentParts.length ? currentParts[i] : 0;
      final latVal = i < latestParts.length ? latestParts[i] : 0;
      if (currVal > latVal) return 1;
      if (latVal > currVal) return -1;
    }
    final currentPre = currentSeparator < 0
        ? null
        : cleanCurrent.substring(currentSeparator + 1);
    final latestPre =
        latestSeparator < 0 ? null : cleanLatest.substring(latestSeparator + 1);
    if (currentPre == latestPre) return 0;
    if (currentPre == null) return 1;
    if (latestPre == null) return -1;
    return _comparePrerelease(currentPre, latestPre);
  }

  static int _comparePrerelease(String current, String latest) {
    final currentParts = current.split('.');
    final latestParts = latest.split('.');
    final length = currentParts.length > latestParts.length ? currentParts.length : latestParts.length;
    for (var i = 0; i < length; i++) {
      if (i >= currentParts.length) return -1;
      if (i >= latestParts.length) return 1;
      final currentNumber = int.tryParse(currentParts[i]);
      final latestNumber = int.tryParse(latestParts[i]);
      if (currentNumber != null && latestNumber != null) {
        if (currentNumber != latestNumber) return currentNumber > latestNumber ? 1 : -1;
      } else if (currentNumber != null) {
        return -1;
      } else if (latestNumber != null) {
        return 1;
      } else {
        final comparison = currentParts[i].compareTo(latestParts[i]);
        if (comparison != 0) return comparison > 0 ? 1 : -1;
      }
    }
    return 0;
  }

  static Future<UpdateResult> checkForUpdates() async {
    final client = HttpClient();
    try {
      client.connectionTimeout = const Duration(seconds: 8);
      
      final url = Uri.parse('https://api.github.com/repos/$repositoryOwner/$repositoryName/releases/latest');
      final request = await client.getUrl(url);
      request.headers.set('Accept', 'application/vnd.github+json');
      request.headers.set('User-Agent', 'Ixeken-Live-Wallpaper');

      final response = await request.close().timeout(const Duration(seconds: 12));
      if (response.statusCode == 200) {
        final responseBody = await response.transform(utf8.decoder).join().timeout(const Duration(seconds: 12));
        final json = jsonDecode(responseBody) as Map<String, dynamic>;

        final tagName = json['tag_name'] as String? ?? '';
        final htmlUrl = json['html_url'] as String? ?? '';
        final body = json['body'] as String?;

        String apkUrl = htmlUrl;
        if (json.containsKey('assets') && json['assets'] is List) {
          final assets = json['assets'] as List;
          for (final asset in assets) {
            if (asset is Map<String, dynamic>) {
              final name = asset['name'] as String? ?? '';
              if (name.toLowerCase().endsWith('.apk')) {
                apkUrl = asset['browser_download_url'] as String? ?? htmlUrl;
                break;
              }
            }
          }
        }

        final currentAppVersion = await getCurrentAppVersion();
        final comparison = compareVersions(currentAppVersion, tagName);
        if (comparison < 0) {
          return NewVersionResult(
            version: tagName,
            downloadUrl: apkUrl,
            changelog: body,
          );
        } else if (comparison > 0) {
          return const FutureVersionResult();
        } else {
          return const UpToDateResult();
        }
      } else {
        return ErrorResult('HTTP ${response.statusCode}');
      }
    } catch (e) {
      return ErrorResult(e.toString());
    } finally {
      client.close(force: true);
    }
  }
}
