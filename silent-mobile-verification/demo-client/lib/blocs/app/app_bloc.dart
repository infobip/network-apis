import 'dart:async';

import 'package:bloc/bloc.dart';
import 'package:infobip_mi_demo_app/repositories/shared_preferences_repository.dart';

import '../../config/app_config.dart';

part 'app_event.dart';

part 'app_state.dart';

class AppBloc extends Bloc<AppEvent, AppState> {
  AppBloc() : super(AppState(false, null, AppConfiguration.defaultLocation)) {
    on<DebugChanged>(_onDebugChanged);
    on<ApiKeyChanged>(_onApiKeyChanged);
    on<LocationChanged>(_onLocationChanged);
    _subscribeToRepository();
  }

  final SharedPreferencesRepository sharedPreferencesRepository =
      SharedPreferencesRepository.instance();
  late StreamSubscription<bool?> _debugSubscription;
  late StreamSubscription<String?> _apiKeySubscription;
  late StreamSubscription<Location?> _locationSubscription;

  void _onDebugChanged(
    DebugChanged event,
    Emitter<AppState> emit,
  ) {
    emit(state.copyWith(debug: event.debug));
  }

  void _onApiKeyChanged(
    ApiKeyChanged event,
    Emitter<AppState> emit,
  ) {
    emit(state.copyWith(apiKey: event.apiKey));
  }

  void _onLocationChanged(
    LocationChanged event,
    Emitter<AppState> emit,
  ) {
    emit(state.copyWith(location: event.location));
  }

  void _subscribeToRepository() {
    _debugSubscription = sharedPreferencesRepository.debug.listen((debug) {
      if (debug != null) {
        add(DebugChanged(debug));
      } else {
        sharedPreferencesRepository.saveDebug(false);
      }
    });
    _apiKeySubscription = sharedPreferencesRepository.apiKey
        .listen((apiKey) => add(ApiKeyChanged(apiKey)));
    _locationSubscription =
        sharedPreferencesRepository.location.listen((location) {
      if (location != null) {
        add(LocationChanged(location));
      } else {
        sharedPreferencesRepository
            .saveLocation(AppConfiguration.defaultLocation);
      }
    });
  }

  @override
  Future<void> close() {
    _debugSubscription.cancel();
    _apiKeySubscription.cancel();
    _locationSubscription.cancel();
    return super.close();
  }
}
