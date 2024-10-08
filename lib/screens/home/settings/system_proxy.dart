import 'dart:io';

import 'package:flutter/material.dart';

import '../../../utils/platform_system_proxy_user.dart';
import '../../../utils/prefs.dart';
import '../../../utils/vpn_manager.dart';

class SystemProxyScreen extends StatefulWidget {
  const SystemProxyScreen({
    super.key,
  });

  @override
  State<SystemProxyScreen> createState() => _SystemProxyScreenState();
}

class _SystemProxyScreenState extends State<SystemProxyScreen> {
  bool? _systemProxyIsEnabled = false;
  bool _systemProxyShouldEnable = false;

  @override
  void initState() {
    super.initState();
    _loadSettings();
  }

  _loadSettings() async {
    _systemProxyIsEnabled = await platformSystemProxyUser.isEnabled();
    _systemProxyShouldEnable = prefs.getBool('systemProxy')!;
    setState(() {
      _systemProxyIsEnabled = _systemProxyIsEnabled;
      _systemProxyShouldEnable = _systemProxyShouldEnable;
    });
  }

  @override
  Widget build(BuildContext context) {
    final fields = [
      ListTile(
        enabled: _systemProxyIsEnabled != null,
        title: const Text("Enable system proxy"),
        subtitle: Text(
            "Provided by ${Platform.operatingSystem}, not all apps respect this setting"),
        trailing: Switch(
          value: _systemProxyIsEnabled == null ? false : _systemProxyShouldEnable,
          onChanged: (bool shouldEnable) {
            setState(() {
              _systemProxyShouldEnable = shouldEnable;
            });
            prefs.setWithNotification('systemProxy', shouldEnable);
            vPNMan.getIsCoreActive().then((isCoreActive) {
              if (isCoreActive) {
                if (shouldEnable) {
                  vPNMan.startSystemProxy();
                } else {
                  vPNMan.stopSystemProxy();
                }
              }
            });
          },
        ),
      ),
    ];
    return Scaffold(
        appBar: AppBar(
          // Use the selected tab's label for the AppBar title
          title: const Text("SystemProxy"),
        ),
        body: ListView.builder(
          itemCount: fields.length,
          itemBuilder: (context, index) => fields[index],
        ));
  }
}
