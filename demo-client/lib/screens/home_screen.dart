import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:infobip_mi_demo_app/widgets/app_drawer.dart';

import '../blocs/app/app_bloc.dart';
import '../blocs/navigation/navigation_bloc.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  static Page page() => const MaterialPage<void>(child: HomeScreen());

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: HamburgerAppBar(context: context, title: 'Home'),
      drawer: const AppDrawer(),
      body: BlocBuilder<AppBloc, AppState>(
        builder: (context, appState) => Padding(
          padding: const EdgeInsets.all(17),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 17),
              if (appState.isApiKeySet) ...{
                Text(
                  'Welcome',
                  style: Theme.of(context).textTheme.labelMedium,
                )
              } else ...{
                Text('Please set your API key',
                    style: Theme.of(context).textTheme.labelMedium),
                const SizedBox(
                  height: 30,
                ),
                TextButton(
                  child: Text(
                    'Configure API key',
                    style: Theme.of(context)
                        .textTheme
                        .labelMedium
                        ?.copyWith(color: Theme.of(context).primaryColor),
                  ),
                  onPressed: () => context
                      .read<NavigationBloc>()
                      .add(SettingsPageRequested()),
                ),
              }
            ],
          ),
        ),
      ),
    );
  }
}
