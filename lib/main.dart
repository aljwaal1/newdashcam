import 'dart:async';
import 'dart:io';
import 'dart:math';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:share_plus/share_plus.dart';
import 'package:video_player/video_player.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const DashCamApp());
}

class DashCamApp extends StatelessWidget {
  const DashCamApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'داش كام بدون صوت',
      theme: ThemeData(
        brightness: Brightness.dark,
        useMaterial3: true,
        fontFamily: 'Roboto',
        scaffoldBackgroundColor: const Color(0xFF080B10),
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF4EA3FF),
          brightness: Brightness.dark,
        ),
      ),
      home: const DashCamHomePage(),
    );
  }
}

class DashRecording {
  DashRecording({required this.file, required this.dateTime, this.speedKmh});

  final File file;
  final DateTime dateTime;
  final double? speedKmh;
}

class DashCamHomePage extends StatefulWidget {
  const DashCamHomePage({super.key});

  @override
  State<DashCamHomePage> createState() => _DashCamHomePageState();
}

class _DashCamHomePageState extends State<DashCamHomePage> with WidgetsBindingObserver {
  final DateFormat _dateFormat = DateFormat('yyyy-MM-dd  HH:mm:ss');
  final DateFormat _fileDateFormat = DateFormat('yyyyMMdd_HHmmss');
  final DateFormat _listDateFormat = DateFormat('yyyy-MM-dd  HH:mm');

  List<CameraDescription> _cameras = <CameraDescription>[];
  CameraController? _controller;
  int _selectedCameraIndex = 0;
  bool _loading = true;
  bool _recording = false;
  bool _busy = false;
  String? _error;

  DateTime _now = DateTime.now();
  double? _speedKmh;
  Timer? _clockTimer;
  Timer? _segmentTimer;
  StreamSubscription<Position>? _positionSub;

  int _segmentMinutes = 3;
  int _keepLast = 50;
  List<DashRecording> _recordings = <DashRecording>[];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _now = DateTime.now());
    });
    _start();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _clockTimer?.cancel();
    _segmentTimer?.cancel();
    _positionSub?.cancel();
    _controller?.dispose();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    final CameraController? cameraController = _controller;
    if (cameraController == null || !cameraController.value.isInitialized) return;
    if (state == AppLifecycleState.inactive || state == AppLifecycleState.paused) {
      if (_recording) {
        _stopRecording();
      }
      cameraController.dispose();
    } else if (state == AppLifecycleState.resumed) {
      _initCamera(_selectedCameraIndex);
    }
  }

  Future<void> _start() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    final bool ok = await _requestPermissions();
    if (!ok) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'يرجى السماح للكاميرا والموقع حتى يعمل التطبيق.';
      });
      return;
    }

    await _startLocation();
    await _loadRecordings();

    try {
      _cameras = await availableCameras();
      if (_cameras.isEmpty) {
        throw Exception('لا توجد كاميرا متاحة على هذا الجهاز.');
      }
      final int backIndex = _cameras.indexWhere((CameraDescription c) => c.lensDirection == CameraLensDirection.back);
      _selectedCameraIndex = backIndex >= 0 ? backIndex : 0;
      await _initCamera(_selectedCameraIndex);
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = 'تعذر تشغيل الكاميرا: $e';
      });
    }
  }

  Future<bool> _requestPermissions() async {
    final PermissionStatus camera = await Permission.camera.request();
    final PermissionStatus location = await Permission.locationWhenInUse.request();
    return camera.isGranted && location.isGranted;
  }

  Future<void> _startLocation() async {
    try {
      final bool serviceEnabled = await Geolocator.isLocationServiceEnabled();
      if (!serviceEnabled) return;
      LocationPermission permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) return;

      const LocationSettings settings = LocationSettings(
        accuracy: LocationAccuracy.best,
        distanceFilter: 1,
      );
      await _positionSub?.cancel();
      _positionSub = Geolocator.getPositionStream(locationSettings: settings).listen((Position position) {
        final double kmh = max(0, position.speed * 3.6);
        if (mounted) setState(() => _speedKmh = kmh);
      });
    } catch (_) {
      // Speed is optional. The app must still record even if GPS is unavailable.
    }
  }

  Future<Directory> _recordingsDir() async {
    final Directory dir = await getApplicationDocumentsDirectory();
    final Directory dashDir = Directory('${dir.path}/dashcam_recordings');
    if (!await dashDir.exists()) await dashDir.create(recursive: true);
    return dashDir;
  }

  Future<void> _loadRecordings() async {
    try {
      final Directory dir = await _recordingsDir();
      final List<FileSystemEntity> files = dir
          .listSync()
          .where((FileSystemEntity entity) => entity is File && entity.path.toLowerCase().endsWith('.mp4'))
          .toList();
      files.sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));
      final List<DashRecording> items = files.map((FileSystemEntity entity) {
        final File file = File(entity.path);
        return DashRecording(file: file, dateTime: file.statSync().modified);
      }).toList();
      if (mounted) setState(() => _recordings = items);
    } catch (_) {
      if (mounted) setState(() => _recordings = <DashRecording>[]);
    }
  }

  Future<void> _initCamera(int index) async {
    setState(() {
      _loading = true;
      _error = null;
    });
    await _controller?.dispose();

    final CameraController controller = CameraController(
      _cameras[index],
      ResolutionPreset.high,
      enableAudio: false,
      imageFormatGroup: ImageFormatGroup.yuv420,
    );

    await controller.initialize();
    if (!mounted) {
      await controller.dispose();
      return;
    }
    setState(() {
      _controller = controller;
      _selectedCameraIndex = index;
      _loading = false;
    });
  }

  Future<void> _switchCamera() async {
    if (_cameras.length < 2 || _busy || _recording) return;
    final int next = (_selectedCameraIndex + 1) % _cameras.length;
    await _initCamera(next);
  }

  Future<void> _startRecording() async {
    final CameraController? controller = _controller;
    if (controller == null || !controller.value.isInitialized || _recording || _busy) return;
    setState(() => _busy = true);
    try {
      await controller.startVideoRecording();
      _segmentTimer?.cancel();
      _segmentTimer = Timer(Duration(minutes: _segmentMinutes), _rotateSegment);
      if (!mounted) return;
      setState(() {
        _recording = true;
        _busy = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = 'تعذر بدء التسجيل: $e';
      });
    }
  }

  Future<void> _rotateSegment() async {
    if (!_recording) return;
    await _stopRecording(restart: true);
  }

  Future<void> _stopRecording({bool restart = false}) async {
    final CameraController? controller = _controller;
    if (controller == null || !controller.value.isRecordingVideo || _busy) return;
    setState(() => _busy = true);
    _segmentTimer?.cancel();
    try {
      final XFile xfile = await controller.stopVideoRecording();
      final Directory dir = await _recordingsDir();
      final DateTime created = DateTime.now();
      final String fileName = 'DashCam_${_fileDateFormat.format(created)}.mp4';
      final File target = File('${dir.path}/$fileName');
      await File(xfile.path).copy(target.path);
      await _cleanupOldRecordings();
      await _loadRecordings();
      if (!mounted) return;
      setState(() {
        _recording = false;
        _busy = false;
      });
      if (restart && mounted) await _startRecording();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _recording = false;
        _busy = false;
        _error = 'تعذر حفظ التسجيل: $e';
      });
    }
  }

  Future<void> _cleanupOldRecordings() async {
    final Directory dir = await _recordingsDir();
    final List<File> files = dir
        .listSync()
        .whereType<File>()
        .where((File file) => file.path.toLowerCase().endsWith('.mp4'))
        .toList();
    files.sort((a, b) => b.statSync().modified.compareTo(a.statSync().modified));
    if (files.length <= _keepLast) return;
    for (final File file in files.skip(_keepLast)) {
      try {
        await file.delete();
      } catch (_) {}
    }
  }

  Future<void> _deleteRecording(File file) async {
    try {
      if (await file.exists()) await file.delete();
      await _loadRecordings();
    } catch (_) {}
  }

  String _speedText() {
    final double speed = _speedKmh ?? 0;
    return '${speed.round()} km/h';
  }

  String _fileSize(File file) {
    try {
      final int bytes = file.lengthSync();
      if (bytes < 1024 * 1024) return '${(bytes / 1024).toStringAsFixed(0)} KB';
      return '${(bytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    } catch (_) {
      return '';
    }
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('داش كام بدون صوت'),
          centerTitle: true,
          backgroundColor: const Color(0xFF0E1420),
          actions: <Widget>[
            IconButton(
              tooltip: 'تبديل الكاميرا',
              onPressed: _recording ? null : _switchCamera,
              icon: const Icon(Icons.cameraswitch_rounded),
            ),
          ],
        ),
        body: _loading
            ? const Center(child: CircularProgressIndicator())
            : _error != null
                ? _ErrorPanel(message: _error!, onRetry: _start)
                : Column(
                    children: <Widget>[
                      Expanded(flex: 6, child: _cameraPreview()),
                      _controls(),
                      Expanded(flex: 5, child: _recordingsList()),
                    ],
                  ),
      ),
    );
  }

  Widget _cameraPreview() {
    final CameraController? controller = _controller;
    if (controller == null || !controller.value.isInitialized) {
      return const Center(child: Text('الكاميرا غير جاهزة'));
    }

    return Container(
      margin: const EdgeInsets.fromLTRB(12, 12, 12, 6),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
        color: Colors.black,
      ),
      clipBehavior: Clip.antiAlias,
      child: Stack(
        fit: StackFit.expand,
        children: <Widget>[
          CameraPreview(controller),
          Positioned(
            top: 10,
            left: 12,
            child: _TinyDashText(text: _recording ? '● Rec' : 'Ready', active: _recording),
          ),
          Positioned(
            top: 10,
            right: 12,
            child: _TinyDashText(text: _dateFormat.format(_now)),
          ),
          Positioned(
            bottom: 12,
            left: 12,
            child: _TinyDashText(text: _speedText()),
          ),
        ],
      ),
    );
  }

  Widget _controls() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF0E1420),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Column(
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: FilledButton.icon(
                  onPressed: _busy ? null : (_recording ? () => _stopRecording() : _startRecording),
                  icon: Icon(_recording ? Icons.stop_rounded : Icons.fiber_manual_record_rounded),
                  label: Text(_recording ? 'إيقاف التسجيل' : 'بدء التسجيل'),
                  style: FilledButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    backgroundColor: _recording ? const Color(0xFFB3261E) : const Color(0xFF1E88E5),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: <Widget>[
              Expanded(child: _smallSelector('مدة المقطع', _segmentMinutes, const <int>[1, 3, 5], (int value) => setState(() => _segmentMinutes = value), 'د')),
              const SizedBox(width: 10),
              Expanded(child: _smallSelector('حفظ آخر', _keepLast, const <int>[10, 20, 50, 100], (int value) => setState(() => _keepLast = value), '')),
            ],
          ),
        ],
      ),
    );
  }

  Widget _smallSelector(String label, int value, List<int> values, ValueChanged<int> onChanged, String suffix) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        color: const Color(0xFF121A28),
        borderRadius: BorderRadius.circular(14),
      ),
      child: DropdownButtonHideUnderline(
        child: DropdownButton<int>(
          value: value,
          isExpanded: true,
          dropdownColor: const Color(0xFF121A28),
          items: values.map((int item) {
            return DropdownMenuItem<int>(value: item, child: Text('$label: $item $suffix'));
          }).toList(),
          onChanged: _recording ? null : (int? newValue) {
            if (newValue != null) onChanged(newValue);
          },
        ),
      ),
    );
  }

  Widget _recordingsList() {
    if (_recordings.isEmpty) {
      return const Center(
        child: Text('لا توجد تسجيلات بعد', style: TextStyle(color: Colors.white70)),
      );
    }
    return ListView.separated(
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 16),
      itemCount: _recordings.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (BuildContext context, int index) {
        final DashRecording item = _recordings[index];
        return Container(
          decoration: BoxDecoration(
            color: const Color(0xFF0E1420),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
          ),
          child: ListTile(
            leading: const CircleAvatar(
              backgroundColor: Color(0xFF162338),
              child: Icon(Icons.videocam_rounded),
            ),
            title: Text(_listDateFormat.format(item.dateTime), textDirection: TextDirection.ltr),
            subtitle: Text(_fileSize(item.file), textDirection: TextDirection.ltr),
            trailing: PopupMenuButton<String>(
              onSelected: (String value) async {
                if (value == 'play') {
                  Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => VideoPlaybackPage(file: item.file, dateTime: item.dateTime)));
                } else if (value == 'share') {
                  await Share.shareXFiles(<XFile>[XFile(item.file.path)], text: 'Dash Cam');
                } else if (value == 'delete') {
                  await _deleteRecording(item.file);
                }
              },
              itemBuilder: (_) => const <PopupMenuEntry<String>>[
                PopupMenuItem<String>(value: 'play', child: Text('تشغيل')),
                PopupMenuItem<String>(value: 'share', child: Text('مشاركة')),
                PopupMenuItem<String>(value: 'delete', child: Text('حذف')),
              ],
            ),
            onTap: () {
              Navigator.of(context).push(MaterialPageRoute<void>(builder: (_) => VideoPlaybackPage(file: item.file, dateTime: item.dateTime)));
            },
          ),
        );
      },
    );
  }
}

class _TinyDashText extends StatelessWidget {
  const _TinyDashText({required this.text, this.active = false});

  final String text;
  final bool active;

  @override
  Widget build(BuildContext context) {
    return Text(
      text,
      textDirection: TextDirection.ltr,
      style: TextStyle(
        fontSize: 12,
        height: 1.0,
        fontWeight: FontWeight.w600,
        color: active ? const Color(0xFFFFE0E0) : Colors.white,
        shadows: const <Shadow>[
          Shadow(color: Colors.black, blurRadius: 4, offset: Offset(0, 1)),
          Shadow(color: Colors.black, blurRadius: 8, offset: Offset(0, 1)),
        ],
      ),
    );
  }
}

class _ErrorPanel extends StatelessWidget {
  const _ErrorPanel({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            const Icon(Icons.error_outline_rounded, size: 52, color: Colors.orangeAccent),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh_rounded), label: const Text('إعادة المحاولة')),
          ],
        ),
      ),
    );
  }
}

class VideoPlaybackPage extends StatefulWidget {
  const VideoPlaybackPage({super.key, required this.file, required this.dateTime});

  final File file;
  final DateTime dateTime;

  @override
  State<VideoPlaybackPage> createState() => _VideoPlaybackPageState();
}

class _VideoPlaybackPageState extends State<VideoPlaybackPage> {
  late final VideoPlayerController _controller;
  final DateFormat _format = DateFormat('yyyy-MM-dd  HH:mm:ss');
  bool _ready = false;

  @override
  void initState() {
    super.initState();
    _controller = VideoPlayerController.file(widget.file)
      ..initialize().then((_) {
        if (!mounted) return;
        setState(() => _ready = true);
        _controller.play();
      });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: TextDirection.rtl,
      child: Scaffold(
        appBar: AppBar(title: const Text('تشغيل التسجيل')),
        body: Center(
          child: _ready
              ? GestureDetector(
                  onTap: () {
                    setState(() {
                      _controller.value.isPlaying ? _controller.pause() : _controller.play();
                    });
                  },
                  child: AspectRatio(
                    aspectRatio: _controller.value.aspectRatio,
                    child: Stack(
                      fit: StackFit.expand,
                      children: <Widget>[
                        VideoPlayer(_controller),
                        Positioned(top: 10, right: 12, child: _TinyDashText(text: _format.format(widget.dateTime))),
                        const Positioned(bottom: 12, left: 12, child: _TinyDashText(text: 'Recorded')),
                      ],
                    ),
                  ),
                )
              : const CircularProgressIndicator(),
        ),
        floatingActionButton: FloatingActionButton(
          onPressed: () {
            setState(() {
              _controller.value.isPlaying ? _controller.pause() : _controller.play();
            });
          },
          child: Icon(_controller.value.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded),
        ),
      ),
    );
  }
}
