import 'package:flutter/material.dart';
import 'package:flutter_form_bloc/flutter_form_bloc.dart';
import 'package:infobip_mi_demo_app/blocs/app/app_bloc.dart';
import 'package:infobip_mi_demo_app/config/app_config.dart';
import 'package:infobip_mi_demo_app/widgets/app_drawer.dart';
import 'package:infobip_mi_demo_app/widgets/settings/debug_dialog.dart';

import '../widgets/settings/apikey_dialog.dart';
import '../widgets/settings/location_dialog.dart';

class SettingsScreen extends StatelessWidget {
  const SettingsScreen({super.key});

  static Page page() => const MaterialPage<void>(child: SettingsScreen());

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: HamburgerAppBar(context: context, title: 'Settings'),
        drawer: const AppDrawer(),
        body: SingleChildScrollView(
          child: Padding(
              padding: const EdgeInsets.all(17),
              child: buildSettingsItems(context)),
        ),
      );

  BlocBuilder buildSettingsItems(BuildContext context) =>
      BlocBuilder<AppBloc, AppState>(
        builder: (context, appState) => Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const SizedBox(height: 17),
            Stack(
              children: [
                TextField(
                  controller: TextEditingController(
                    text: appState.debug ? 'Enabled' : 'Disabled',
                  ),
                  style: const TextStyle(color: Colors.black),
                  decoration: const InputDecoration(
                    labelText: 'Debug',
                    icon: Icon(Icons.code),
                  ),
                  readOnly: true,
                ),
                Positioned.fill(
                  child: GestureDetector(
                    onTap: () => _showEditDebugDialog(context, appState),
                    behavior: HitTestBehavior.translucent,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 17),
            Stack(
              children: [
                TextField(
                  controller: TextEditingController(text: appState.apiKey),
                  style: const TextStyle(color: Colors.black),
                  decoration: const InputDecoration(
                    labelText: 'API Key',
                    icon: Icon(Icons.vpn_key_outlined),
                  ),
                  readOnly: true,
                ),
                Positioned.fill(
                  child: GestureDetector(
                    onTap: () => _showEditApiKeyDialog(context, appState),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 17),
            Stack(
              children: [
                TextField(
                  controller: TextEditingController(
                    text: appState.location.title,
                  ),
                  style: const TextStyle(color: Colors.black),
                  decoration: const InputDecoration(
                    labelText: 'Location',
                    icon: Icon(Icons.dns_outlined),
                  ),
                  readOnly: true,
                ),
                Positioned.fill(
                  child: GestureDetector(
                    onTap: () => _showEditLocationDialog(context, appState),
                    behavior: HitTestBehavior.translucent,
                  ),
                ),
              ],
            ),
          ],
        ),
      );

  Future<void> _showEditDebugDialog(
      BuildContext context, AppState appState) async {
    await showDialog(
      context: context,
      builder: (BuildContext context) => DebugDialog(appState.debug),
    );
  }

  Future<void> _showEditApiKeyDialog(
      BuildContext context, AppState appState) async {
    await showDialog(
      context: context,
      builder: (BuildContext context) => ApiKeyDialog(appState.apiKey),
    );
  }

  Future<void> _showEditLocationDialog(
      BuildContext context, AppState appState) async {
    await showDialog(
      context: context,
      builder: (BuildContext context) => LocationDialog(appState.location),
    );
  }
}
