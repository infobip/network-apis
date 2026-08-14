import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../blocs/navigation/navigation_bloc.dart';

class ApiKeyWarning extends StatelessWidget {
  const ApiKeyWarning({super.key});

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const ListTile(
            minVerticalPadding: 16,
            leading:
                Icon(Icons.warning, color: Colors.deepOrange, size: 50),
            title: Text('Missing API Key!'),
            subtitle: Text('You have to set API Key in settings.',
                style: TextStyle(color: Colors.black54)),
          ),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: <Widget>[
              TextButton(
                child: const Text('SET API KEY'),
                onPressed: () =>
                    context.read<NavigationBloc>().add(SettingsPageRequested()),
              ),
              const SizedBox(width: 8),
            ],
          ),
        ],
      ),
    );
  }
}
