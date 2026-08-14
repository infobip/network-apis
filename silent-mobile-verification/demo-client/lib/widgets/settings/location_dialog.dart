import 'package:flutter/material.dart';
import 'package:flutter_form_bloc/flutter_form_bloc.dart';
import 'package:infobip_mi_demo_app/config/app_config.dart';
import 'package:infobip_mi_demo_app/widgets/loading_dialog.dart';

import '../../blocs/form_blocs/settings/location_form_bloc.dart';

class LocationDialog extends StatelessWidget {
  final Location initialLocationValue;

  const LocationDialog(this.initialLocationValue, {super.key});

  @override
  Widget build(BuildContext context) => BlocProvider(
    create: (_) => LocationFormBloc(initialLocationValue),
    child: _createLocationDialog(),
  );

  Widget _createLocationDialog() => Builder(builder: (context) {
    final locationForm = context.read<LocationFormBloc>();
    return AlertDialog(
      title: Text('Location',
          style: Theme.of(context).textTheme.labelMedium),
      content: SingleChildScrollView(
        child: FormBlocListener<LocationFormBloc, Location, String>(
          onSubmitting: (context, state) {
            LoadingDialog.show(context);
          },
          onSuccess: (context, state) {
            LoadingDialog.hide(context);
            Navigator.pop(context);
          },
          child: SizedBox(
            width: MediaQuery.of(context).size.width,
            child: DropdownFieldBlocBuilder(
              showEmptyItem: false,
              selectFieldBloc: locationForm.location,
              itemBuilder: (context, value) => FieldItem(
                child: Text((value as Location).title),
              ),
            ),
          ),
        ),
      ),
      actions: <Widget>[
        ElevatedButton(
            onPressed: locationForm.submit,
            child: const Text('Save'))
      ],
    );
  });
}
