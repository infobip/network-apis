part of 'app_bloc.dart';

class AppState {
  AppState(this.debug, this.apiKey, this.location);

  final bool debug;
  final String? apiKey;
  final Location location;

  AppState copyWith({bool? debug, String? apiKey, Location? location}) {
    return AppState(
        debug ?? this.debug, apiKey ?? this.apiKey, location ?? this.location);
  }

  bool get isApiKeySet => apiKey != null;
}
