import 'package:flutter/material.dart';
import 'package:flutter_form_bloc/flutter_form_bloc.dart';
import 'package:infobip_mi_demo_app/widgets/loading_dialog.dart';

import '../../blocs/form_blocs/settings/apikey_form_bloc.dart';

class ApiKeyDialog extends StatelessWidget {
  final String? initialApiKeyValue;

  const ApiKeyDialog(this.initialApiKeyValue, {super.key});

  @override
  Widget build(BuildContext context) => BlocProvider(
    create: (_) => ApiKeyFormBloc(initialApiKeyValue),
    child: _createApiKeyDialog(),
  );

  Widget _createApiKeyDialog() => Builder(builder: (context) {
    final apiKeyForm = context.read<ApiKeyFormBloc>();
    return AlertDialog(
      title: Text('API Key',
          style: Theme.of(context).textTheme.labelMedium),
      content: SingleChildScrollView(
        child: FormBlocListener<ApiKeyFormBloc, String, String>(
          onSubmitting: (context, state) {
            LoadingDialog.show(context);
          },
          onSuccess: (context, state) {
            LoadingDialog.hide(context);
            Navigator.pop(context);
          },
          onSubmissionFailed: (context, state) {
            LoadingDialog.hide(context);
          },
          child: SizedBox(
            width: MediaQuery.of(context).size.width,
            child: TextFieldBlocBuilder(
              textFieldBloc: apiKeyForm.apiKey,
              keyboardType: TextInputType.text,
              autofocus: true,
              errorBuilder: (context, error) {
                if (error == FieldBlocValidatorsErrors.required) {
                  return 'Invalid API key format';
                }
                return null;
              },
            ),
          ),
        ),
      ),
      actions: <Widget>[
        ElevatedButton(
            onPressed: apiKeyForm.submit,
            child: const Text('Save'))
      ],
    );
  });
}
