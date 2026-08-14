part of 'app_bloc.dart';

abstract class AppEvent {
  const AppEvent();
}

class DebugChanged extends AppEvent {
  const DebugChanged(this.debug);

  final bool debug;
}

class ApiKeyChanged extends AppEvent {
  const ApiKeyChanged(this.apiKey);

  final String? apiKey;
}

class LocationChanged extends AppEvent {
  const LocationChanged(this.location);

  final Location location;
}
