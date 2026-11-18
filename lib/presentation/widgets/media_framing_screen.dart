import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:video_player/video_player.dart';
import '../../wallpaper_manager.dart';

class MediaFramingScreen extends StatefulWidget {
  final String filePath;
  final String targetType; // 'general', 'day', 'night'

  const MediaFramingScreen({
    super.key,
    required this.filePath,
    this.targetType = 'general',
  });

  @override
  State<MediaFramingScreen> createState() => _MediaFramingScreenState();
}

class _MediaFramingScreenState extends State<MediaFramingScreen> {
  VideoPlayerController? _videoController;
  bool _isVideo = false;
  bool _isInitialized = false;
  bool _isProcessing = false;
  bool _framingCalculated = false;

  // Aspect ratio of the media file
  double _mediaAspectRatio = 16 / 9;

  // Normalized horizontal offset of the crop window (0.0 to 1.0 - normalizedCropWidth)
  double _cropX = 0.0;
  double _cropY = 0.0;
  double _cropWidth = 1.0;
  double _cropHeight = 1.0;

  @override
  void initState() {
    super.initState();
    _initMedia();
  }

  Future<void> _initMedia() async {
    final lower = widget.filePath.toLowerCase();
    _isVideo = lower.endsWith('.mp4') || lower.endsWith('.mkv') || lower.endsWith('.mov') || lower.endsWith('.webm');

    if (_isVideo) {
      try {
        _videoController = VideoPlayerController.file(File(widget.filePath));
        await _videoController!.initialize();
        await _videoController!.setLooping(true);
        await _videoController!.setVolume(0.0);
        await _videoController!.play();
        if (mounted) {
          setState(() {
            _mediaAspectRatio = _videoController!.value.aspectRatio;
            _isInitialized = true;
          });
        }
      } catch (e) {
        debugPrint("Error initializing video: $e");
        if (mounted) {
          setState(() => _isInitialized = true);
        }
      }
    } else {
      // For GIF or Image, decode natural aspect ratio
      final file = File(widget.filePath);
      final bytes = await file.readAsBytes();
      final decodedImage = await decodeImageFromList(bytes);
      if (mounted) {
        setState(() {
          _mediaAspectRatio = decodedImage.width / decodedImage.height;
          _isInitialized = true;
        });
      }
    }
  }

  @override
  void dispose() {
    _videoController?.dispose();
    super.dispose();
  }

  void _calculateFraming(double screenAspectRatio) {
    if (_framingCalculated) return;
    _framingCalculated = true;

    // If media is wider than target phone screen (e.g. 16:9 vs 9:20)
    if (_mediaAspectRatio > screenAspectRatio) {
      _cropHeight = 1.0;
      _cropY = 0.0;
      _cropWidth = ((_cropHeight * screenAspectRatio) / _mediaAspectRatio).clamp(0.1, 1.0);
      _cropX = (1.0 - _cropWidth) / 2.0;
    } else {
      // If media is taller than target phone screen
      _cropWidth = 1.0;
      _cropX = 0.0;
      _cropHeight = ((_cropWidth * _mediaAspectRatio) / screenAspectRatio).clamp(0.1, 1.0);
      _cropY = (1.0 - _cropHeight) / 2.0;
    }
  }

  void _setPreset(String preset) {
    HapticFeedback.selectionClick();
    setState(() {
      if (preset == 'left') {
        _cropX = 0.0;
      } else if (preset == 'center') {
        _cropX = (1.0 - _cropWidth) / 2.0;
      } else if (preset == 'right') {
        _cropX = (1.0 - _cropWidth);
      }
      _cropX = _cropX.clamp(0.0, 1.0 - _cropWidth);
    });
  }

  Future<void> _confirmFraming() async {
    setState(() => _isProcessing = true);
    HapticFeedback.lightImpact();

    final result = await WallpaperManager.cropAndProcessMedia(
      filePath: widget.filePath,
      cropX: _cropX,
      cropY: _cropY,
      cropWidth: _cropWidth,
      cropHeight: _cropHeight,
      type: widget.targetType,
    );

    if (mounted) {
      setState(() => _isProcessing = false);
      if (result != null) {
        Navigator.pop(context, result);
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text("Error al procesar el recorte del video."),
            backgroundColor: Colors.redAccent,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final screenSize = MediaQuery.of(context).size;
    final screenAspectRatio = screenSize.width / screenSize.height;

    if (_isInitialized) {
      _calculateFraming(screenAspectRatio);
    }

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.close_rounded, color: Colors.white),
          onPressed: () => Navigator.pop(context),
        ),
        title: const Text(
          "Ajustar Encuadre",
          style: TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 18),
        ),
        centerTitle: true,
      ),
      body: !_isInitialized
          ? const Center(child: CircularProgressIndicator(color: Colors.white))
          : Stack(
              children: [
                // Media preview & framing interactive area
                Center(
                  child: AspectRatio(
                    aspectRatio: _mediaAspectRatio,
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final mediaWidth = constraints.maxWidth;
                        final mediaHeight = constraints.maxHeight;

                        final frameWidth = mediaWidth * _cropWidth;
                        final frameLeft = mediaWidth * _cropX;
                        final frameHeight = mediaHeight * _cropHeight;
                        final frameTop = mediaHeight * _cropY;

                        return GestureDetector(
                          onHorizontalDragUpdate: _cropWidth < 1.0
                              ? (details) {
                                  setState(() {
                                    final deltaFraction = (details.primaryDelta ?? 0.0) / mediaWidth;
                                    _cropX = (_cropX + deltaFraction).clamp(0.0, 1.0 - _cropWidth);
                                  });
                                }
                              : null,
                          onVerticalDragUpdate: _cropHeight < 1.0
                              ? (details) {
                                  setState(() {
                                    final deltaFraction = (details.primaryDelta ?? 0.0) / mediaHeight;
                                    _cropY = (_cropY + deltaFraction).clamp(0.0, 1.0 - _cropHeight);
                                  });
                                }
                              : null,
                          child: Stack(
                            fit: StackFit.expand,
                            children: [
                              // 1. Media (Video or Image)
                              if (_isVideo && _videoController != null && _videoController!.value.isInitialized)
                                VideoPlayer(_videoController!)
                              else
                                Image.file(
                                  File(widget.filePath),
                                  fit: BoxFit.cover,
                                  cacheWidth: 1440,
                                  cacheHeight: 2560,
                                ),

                              // 2. Dimmed curtain on Left
                              if (frameLeft > 0)
                                Positioned(
                                  left: 0,
                                  top: 0,
                                  bottom: 0,
                                  width: frameLeft.clamp(0.0, mediaWidth),
                                  child: Container(
                                    color: Colors.black.withValues(alpha: 0.75),
                                  ),
                                ),

                              // 3. Dimmed curtain on Right
                              if (frameLeft + frameWidth < mediaWidth)
                                Positioned(
                                  left: (frameLeft + frameWidth).clamp(0.0, mediaWidth),
                                  top: 0,
                                  bottom: 0,
                                  right: 0,
                                  child: Container(
                                    color: Colors.black.withValues(alpha: 0.75),
                                  ),
                                ),

                              // 4. Dimmed curtain on Top
                              if (frameTop > 0)
                                Positioned(
                                  left: 0,
                                  right: 0,
                                  top: 0,
                                  height: frameTop.clamp(0.0, mediaHeight),
                                  child: Container(
                                    color: Colors.black.withValues(alpha: 0.75),
                                  ),
                                ),

                              // 5. Dimmed curtain on Bottom
                              if (frameTop + frameHeight < mediaHeight)
                                Positioned(
                                  left: 0,
                                  right: 0,
                                  top: (frameTop + frameHeight).clamp(0.0, mediaHeight),
                                  bottom: 0,
                                  child: Container(
                                    color: Colors.black.withValues(alpha: 0.75),
                                  ),
                                ),

                              // 6. Phone frame border
                              Positioned(
                                left: frameLeft,
                                top: frameTop,
                                width: frameWidth,
                                height: frameHeight,
                                child: Container(
                                  decoration: BoxDecoration(
                                    border: Border.all(
                                      color: Theme.of(context).colorScheme.primary,
                                      width: 2.5,
                                    ),
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                  child: Stack(
                                    children: [
                                      // Top indicator icon
                                      Align(
                                        alignment: Alignment.topCenter,
                                        child: Container(
                                          margin: const EdgeInsets.only(top: 8),
                                          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                                          decoration: BoxDecoration(
                                            color: Colors.black87,
                                            borderRadius: BorderRadius.circular(12),
                                          ),
                                          child: const Row(
                                            mainAxisSize: MainAxisSize.min,
                                            children: [
                                              Icon(Icons.phone_android_rounded, size: 14, color: Colors.white70),
                                              SizedBox(width: 4),
                                              Text(
                                                "Pantalla",
                                                style: TextStyle(color: Colors.white, fontSize: 11, fontWeight: FontWeight.bold),
                                              ),
                                            ],
                                          ),
                                        ),
                                      ),
                                      // Center pan hint
                                      Center(
                                        child: Container(
                                          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                                          decoration: BoxDecoration(
                                            color: Colors.black54,
                                            borderRadius: BorderRadius.circular(20),
                                          ),
                                          child: const Row(
                                            mainAxisSize: MainAxisSize.min,
                                            children: [
                                              Icon(Icons.chevron_left_rounded, color: Colors.white70, size: 18),
                                              Text(
                                                "Desliza",
                                                style: TextStyle(color: Colors.white70, fontSize: 12, fontWeight: FontWeight.w600),
                                              ),
                                              Icon(Icons.chevron_right_rounded, color: Colors.white70, size: 18),
                                            ],
                                          ),
                                        ),
                                      ),
                                    ],
                                  ),
                                ),
                              ),
                            ],
                          ),
                        );
                      },
                    ),
                  ),
                ),

                // Bottom floating controls panel
                Positioned(
                  left: 16,
                  right: 16,
                  bottom: 24,
                  child: Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: const Color(0xFF1E1E22).withValues(alpha: 0.92),
                      borderRadius: BorderRadius.circular(28),
                      border: Border.all(color: Colors.white12),
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Text(
                          "Encuadra la zona que deseas ver en tu pantalla",
                          style: TextStyle(color: Colors.white70, fontSize: 13, fontWeight: FontWeight.w500),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 12),

                        // Quick presets: Left, Center, Right
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            _buildPresetButton("Izquierda", "left"),
                            const SizedBox(width: 8),
                            _buildPresetButton("Centro", "center"),
                            const SizedBox(width: 8),
                            _buildPresetButton("Derecha", "right"),
                          ],
                        ),

                        const SizedBox(height: 16),

                        // Confirm button
                        SizedBox(
                          width: double.infinity,
                          height: 52,
                          child: ElevatedButton(
                            onPressed: _isProcessing ? null : _confirmFraming,
                            style: ElevatedButton.styleFrom(
                              backgroundColor: Theme.of(context).colorScheme.primary,
                              foregroundColor: Colors.white,
                              shape: RoundedRectangleBorder(
                                borderRadius: BorderRadius.circular(24),
                              ),
                              elevation: 0,
                            ),
                            child: _isProcessing
                                ? const Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      SizedBox(
                                        width: 20,
                                        height: 20,
                                        child: CircularProgressIndicator(
                                          strokeWidth: 2.5,
                                          valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                                        ),
                                      ),
                                      SizedBox(width: 12),
                                      Text(
                                        "Optimizando video...",
                                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                      ),
                                    ],
                                  )
                                : const Row(
                                    mainAxisAlignment: MainAxisAlignment.center,
                                    children: [
                                      Icon(Icons.check_rounded, size: 20),
                                      SizedBox(width: 8),
                                      Text(
                                        "Confirmar Encuadre",
                                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15),
                                      ),
                                    ],
                                  ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
    );
  }

  Widget _buildPresetButton(String label, String preset) {
    return InkWell(
      onTap: () => _setPreset(preset),
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.08),
          borderRadius: BorderRadius.circular(16),
        ),
        child: Text(
          label,
          style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600),
        ),
      ),
    );
  }
}
