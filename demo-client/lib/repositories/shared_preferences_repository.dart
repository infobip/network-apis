import 'dart:async';

import 'package:shared_preferences/shared_preferences.dart';

import '../config/app_config.dart';

enum SharedPreferencesKey { debug, apiKey, location }

class SharedPreferencesRepository {
  static SharedPreferencesRepository? _instance;

  final _debugController = StreamController<bool?>();
  final _apiKeyController = StreamController<String?>();
  final _locationController = StreamController<Location?>();

  static SharedPreferencesRepository instance() {
    _instance ??= SharedPreferencesRepository();
    return _instance!;
  }

  static getValue(SharedPreferencesKey key) async {
    SharedPreferences preferences = await SharedPreferences.getInstance();
    String? stringValue = preferences.getString(key.name);
    return stringValue;
  }

  static setValue(SharedPreferencesKey key, String value) async {
    SharedPreferences preferences = await SharedPreferences.getInstance();
    preferences.setString(key.name, value);
  }

  Stream<bool?> get debug async* {
    final debug = await getValue(SharedPreferencesKey.debug) as String?;
    yield debug == null
        ? null
        : debug == 'true'
            ? true
            : false;
    yield* _debugController.stream;
  }

  Stream<String?> get apiKey async* {
    yield await getValue(SharedPreferencesKey.apiKey) as String?;
    yield* _apiKeyController.stream;
  }

  Stream<Location?> get location async* {
    final location =
    await getValue(SharedPreferencesKey.location) as String?;
    yield location == null ? null : Location.fromString(location);
    yield* _locationController.stream;
  }

  void saveDebug(bool debug) {
    setValue(SharedPreferencesKey.debug, debug.toString());
    _debugController.add(debug);
  }

  void saveApiKey(String apiKey) {
    setValue(SharedPreferencesKey.apiKey, apiKey);
    _apiKeyController.add(apiKey);
  }

  void saveLocation(Location location) {
    setValue(SharedPreferencesKey.location, location.name);
    _locationController.add(location);
  }
}
