import 'dart:async';
import 'dart:io';
import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:intl/intl.dart';
import 'package:path_provider/path_provider.dart';
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
        useMaterial3: true,
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF080C10),
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF3AA6FF),
          brightness: Brightness.dark,
        ),
      ),
      home: const DashCamHomePage(),
    );
  }
}

class RecordingItem {
  const RecordingItem({required this.file, required this.modified, required this.size});
  final File file;
  final DateTime modified;
  final int size;
}

class DashCamHomePage extends StatefulWidget {
  const DashCamHomePage({super.key});

  @override
  State<DashCamHomePage> createState() => _DashCamHomePageState();
}

class _DashCamHomePageState extends State<DashCamHomePage> with WidgetsBindingObserver {
  final DateFormat _stampFormat = DateFormat('yyyy-MM-dd  HH:mm:ss');
  final DateFormat _fileFormat = DateFormat('yyyyMMdd_HHmmss');
  final DateFormat _listFormat = DateFormat('yyyy-MM-dd  HH:mm');

  List<CameraDescription> _cameras = const [];
  CameraController? _controller;
  int _cameraIndex = 0;
  bool _initializing = true;
  bool _isRecording = false;
  bool _switchingCamera = false;
  String? _error;
  Timer? _clockTimer;
  Timer? _segmentTimer;
  DateTime _now = DateTime.now();
  double? _speedKmh;
  StreamSubscription<Position>? _positionSub;
  List<RecordingItem> _recordings = const [];
  int _segmentMinutes = 3;
  int _keepCount = 30;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _boot();
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
    if (state == AppLifecycleState.inactive || state == AppLifecycleState.paused) {
      if (_isRecording) {
        _stopRecording();
      }
    }
  }

  Future<void> _boot() async {
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() => _now = DateTime.now());
    });

    try {
      _cameras = await availableCameras();
      if (_cameras.isEmpty) {
        setState(() {
          _error = 'لم يتم العثور على كاميرا في هذا الجهاز';
          _initializing = false;
        });
        return;
      }
      _cameraIndex = _preferredBackCameraIndex();
      await _initCamera();
      await _startLocation();
      await _loadRecordings();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = 'تعذر تشغيل الكاميرا: $e';
        _initializing = false;
      });
    }
  }

  int _preferredBackCameraIndex() {
    final index = _cameras.indexWhere((c) => c.lensDirection == CameraLensDirection.back);
    return index >= 0 ? index : 0;
  }

  Future<void> _initCamera() async {
    setState(() {
      _initializing = true;
      _error = null;
    });

    final old = _controller;
    _controller = null;
    await old?.dispose();

    final controller = CameraController(
      _cameras[_cameraIndex],
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
      _initializing = false;
    });
  }

  Future<Directory> _recordingsDir() async {
    final dir = await getApplicationDocumentsDirectory();
    final folder = Directory('${dir.path}/dashcam_recordings');
    if (!await folder.exists()) await folder.create(recursive: true);
    return folder;
  }

  Future<void> _loadRecordings() async {
    final dir = await _recordingsDir();
    final files = await dir
        .list()
        .where((e) => e is File && e.path.toLowerCase().endsWith('.mp4'))
        .cast<File>()
        .toList();

    final items = <RecordingItem>[];
    for (final file in files) {
      try {
        final stat = await file.stat();
        items.add(RecordingItem(file: file, modified: stat.modified, size: stat.size));
      } catch (_) {}
    }
    items.sort((a, b) => b.modified.compareTo(a.modified));
    if (mounted) setState(() => _recordings = items);
  }

  Future<void> _cleanupOldRecordings() async {
    await _loadRecordings();
    final extra = _recordings.skip(_keepCount).toList();
    for (final item in extra) {
      try {
        if (await item.file.exists()) await item.file.delete();
      } catch (_) {}
    }
    await _loadRecordings();
  }

  Future<void> _startLocation() async {
    try {
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      if (permission == LocationPermission.denied || permission == LocationPermission.deniedForever) {
        return;
      }
      final settings = const LocationSettings(
        accuracy: LocationAccuracy.bestForNavigation,
        distanceFilter: 0,
      );
      _positionSub = Geolocator.getPositionStream(locationSettings: settings).listen((pos) {
        final speed = (pos.speed * 3.6).clamp(0, 399).toDouble();
        if (mounted) setState(() => _speedKmh = speed);
      });
    } catch (_) {}
  }

  Future<void> _startRecording() async {
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized || _isRecording) return;
    try {
      await controller.startVideoRecording();
      if (!mounted) return;
      setState(() => _isRecording = true);
      _segmentTimer?.cancel();
      _segmentTimer = Timer(Duration(minutes: _segmentMinutes), _restartSegment);
    } catch (e) {
      if (!mounted) return;
      _showSnack('تعذر بدء التسجيل');
    }
  }

  Future<void> _restartSegment() async {
    if (!_isRecording) return;
    await _stopRecording(startAgain: true);
  }

  Future<void> _stopRecording({bool startAgain = false}) async {
    final controller = _controller;
    if (controller == null || !_isRecording) return;
    _segmentTimer?.cancel();
    try {
      final file = await controller.stopVideoRecording();
      final dir = await _recordingsDir();
      final name = 'dashcam_${_fileFormat.format(DateTime.now())}.mp4';
      final saved = File('${dir.path}/$name');
      await File(file.path).copy(saved.path);
      try { await File(file.path).delete(); } catch (_) {}
      if (mounted) setState(() => _isRecording = false);
      await _cleanupOldRecordings();
      if (startAgain && mounted) await _startRecording();
    } catch (_) {
      if (mounted) setState(() => _isRecording = false);
    }
  }

  Future<void> _switchCamera() async {
    if (_cameras.length < 2 || _switchingCamera || _isRecording) return;
    setState(() => _switchingCamera = true);
    try {
      _cameraIndex = (_cameraIndex + 1) % _cameras.length;
      await _initCamera();
    } finally {
      if (mounted) setState(() => _switchingCamera = false);
    }
  }

  void _showSnack(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  String _sizeLabel(int bytes) {
    final mb = bytes / (1024 * 1024);
    return '${mb.toStringAsFixed(mb >= 100 ? 0 : 1)} MB';
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: Directionality.of(context),
      child: Scaffold(
        body: SafeArea(
          child: Column(
            children: [
              _buildPreview(),
              _buildControls(),
              Expanded(child: _buildRecordingsList()),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPreview() {
    if (_initializing) {
      return const Expanded(
        flex: 3,
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_error != null) {
      return Expanded(
        flex: 3,
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Text(_error!, textAlign: TextAlign.center),
          ),
        ),
      );
    }
    final controller = _controller;
    if (controller == null || !controller.value.isInitialized) {
      return const Expanded(flex: 3, child: SizedBox.shrink());
    }

    return Expanded(
      flex: 3,
      child: Container(
        margin: const EdgeInsets.fromLTRB(10, 10, 10, 4),
        clipBehavior: Clip.antiAlias,
        decoration: BoxDecoration(
          color: Colors.black,
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: Colors.white.withValues(alpha: 0.08)),
        ),
        child: Stack(
          fit: StackFit.expand,
          children: [
            FittedBox(
              fit: BoxFit.cover,
              child: SizedBox(
                width: controller.value.previewSize?.height ?? 1080,
                height: controller.value.previewSize?.width ?? 1920,
                child: CameraPreview(controller),
              ),
            ),
            _DashStamp(
              dateTime: _stampFormat.format(_now),
              speed: _speedKmh == null ? '-- km/h' : '${_speedKmh!.round()} km/h',
              recording: _isRecording,
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildControls() {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: const Color(0xFF101820),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Colors.white.withValues(alpha: 0.06)),
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _isRecording ? () => _stopRecording() : _startRecording,
                  icon: Icon(_isRecording ? Icons.stop_rounded : Icons.videocam_rounded),
                  label: Text(_isRecording ? 'إيقاف التسجيل' : 'بدء التسجيل'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _isRecording ? const Color(0xFFB93D3D) : const Color(0xFF1D7CCB),
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 14),
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
                  ),
                ),
              ),
              const SizedBox(width: 10),
              IconButton.filledTonal(
                onPressed: _isRecording ? null : _switchCamera,
                icon: const Icon(Icons.cameraswitch_rounded),
                tooltip: 'تبديل الكاميرا',
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: _choiceChips('مدة المقطع', [1, 3, 5], _segmentMinutes, (v) => setState(() => _segmentMinutes = v))),
              const SizedBox(width: 10),
              Expanded(child: _choiceChips('الاحتفاظ', [10, 30, 50, 100], _keepCount, (v) => setState(() => _keepCount = v))),
            ],
          ),
        ],
      ),
    );
  }

  Widget _choiceChips(String title, List<int> values, int selected, ValueChanged<int> onChanged) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: TextStyle(fontSize: 12, color: Colors.white.withValues(alpha: 0.62))),
        const SizedBox(height: 6),
        Wrap(
          spacing: 6,
          children: values.map((v) {
            final isSelected = v == selected;
            return ChoiceChip(
              label: Text(title == 'مدة المقطع' ? '$v د' : '$v'),
              selected: isSelected,
              onSelected: _isRecording ? null : (_) => onChanged(v),
              visualDensity: VisualDensity.compact,
            );
          }).toList(),
        ),
      ],
    );
  }

  Widget _buildRecordingsList() {
    if (_recordings.isEmpty) {
      return Center(
        child: Text(
          'لا توجد تسجيلات بعد',
          style: TextStyle(color: Colors.white.withValues(alpha: 0.64)),
        ),
      );
    }
    return RefreshIndicator(
      onRefresh: _loadRecordings,
      child: ListView.separated(
        padding: const EdgeInsets.fromLTRB(10, 6, 10, 14),
        itemCount: _recordings.length,
        separatorBuilder: (_, __) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          final item = _recordings[index];
          return Container(
            decoration: BoxDecoration(
              color: const Color(0xFF101820),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.white.withValues(alpha: 0.05)),
            ),
            child: ListTile(
              leading: const Icon(Icons.movie_rounded),
              title: Text(_listFormat.format(item.modified)),
              subtitle: Text(_sizeLabel(item.size)),
              onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => VideoPage(file: item.file))),
              trailing: Wrap(
                spacing: 2,
                children: [
                  IconButton(
                    tooltip: 'مشاركة',
                    icon: const Icon(Icons.share_rounded),
                    onPressed: () => Share.shareXFiles([XFile(item.file.path)]),
                  ),
                  IconButton(
                    tooltip: 'حذف',
                    icon: const Icon(Icons.delete_outline_rounded),
                    onPressed: () async {
                      await item.file.delete();
                      await _loadRecordings();
                    },
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _DashStamp extends StatelessWidget {
  const _DashStamp({required this.dateTime, required this.speed, required this.recording});

  final String dateTime;
  final String speed;
  final bool recording;

  @override
  Widget build(BuildContext context) {
    final textStyle = TextStyle(
      fontSize: 12,
      height: 1.0,
      fontWeight: FontWeight.w600,
      color: Colors.white.withValues(alpha: 0.92),
      shadows: const [
        Shadow(offset: Offset(0.8, 0.8), blurRadius: 2, color: Colors.black87),
      ],
    );

    return IgnorePointer(
      child: Stack(
        children: [
          Positioned(
            left: 12,
            top: 10,
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (recording) ...[
                  Container(
                    width: 7,
                    height: 7,
                    decoration: const BoxDecoration(color: Color(0xFFFF3B30), shape: BoxShape.circle),
                  ),
                  const SizedBox(width: 5),
                  Text('REC', style: textStyle),
                  const SizedBox(width: 10),
                ],
                Text(speed, style: textStyle),
              ],
            ),
          ),
          Positioned(
            right: 12,
            top: 10,
            child: Text(dateTime, style: textStyle),
          ),
        ],
      ),
    );
  }
}

class VideoPage extends StatefulWidget {
  const VideoPage({super.key, required this.file});
  final File file;

  @override
  State<VideoPage> createState() => _VideoPageState();
}

class _VideoPageState extends State<VideoPage> {
  late final VideoPlayerController _controller;
  bool _ready = false;

  @override
  void initState() {
    super.initState();
    _controller = VideoPlayerController.file(widget.file)
      ..initialize().then((_) {
        if (mounted) setState(() => _ready = true);
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
      textDirection: Directionality.of(context),
      child: Scaffold(
        appBar: AppBar(title: const Text('تشغيل التسجيل')),
        body: Center(
          child: _ready
              ? AspectRatio(
                  aspectRatio: _controller.value.aspectRatio,
                  child: VideoPlayer(_controller),
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
