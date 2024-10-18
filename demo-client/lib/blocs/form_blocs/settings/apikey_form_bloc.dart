import 'package:flutter_form_bloc/flutter_form_bloc.dart';
import 'package:infobip_mi_demo_app/repositories/shared_preferences_repository.dart';

class ApiKeyFormBloc extends FormBloc<String, String> {
  late TextFieldBloc apiKey;
  final _sharedRepository = SharedPreferencesRepository.instance();

  ApiKeyFormBloc(String? apiKeyValue) {
    apiKey = TextFieldBloc(
        initialValue: apiKeyValue ?? "",
        validators: [
          FieldBlocValidators.required
        ]
    );

    addFieldBloc(fieldBloc: apiKey);
  }

  @override
  void onSubmitting() {
    try {
      _sharedRepository.saveApiKey(apiKey.value);
      emitSuccess(canSubmitAgain: true);
    } catch (e) {
      emitFailure();
    }
  }
}
