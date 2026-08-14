import 'dart:async';

import 'package:flutter_form_bloc/flutter_form_bloc.dart';

import '../../../config/app_config.dart';
import '../../../repositories/shared_preferences_repository.dart';

class LocationFormBloc extends FormBloc<Location, String> {
  late SelectFieldBloc location;
  final _sharedRepository = SharedPreferencesRepository.instance();

  LocationFormBloc(Location locationValue) {
    location = SelectFieldBloc(
      items: AppConfiguration.locations,
      initialValue: locationValue,
      validators: [FieldBlocValidators.required],
    );

    addFieldBloc(fieldBloc: location);
  }

  @override
  FutureOr<void> onSubmitting() {
    try {
      _sharedRepository.saveLocation(location.value);
      emitSuccess(canSubmitAgain: true);
    } catch (e) {
      emitFailure();
    }
  }
}