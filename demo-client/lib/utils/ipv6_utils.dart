import 'dart:io';

class IPv6Utils {
  static Future<bool> checkIfDeviceIsOnIpv6Network() async {
    final interfaces = await NetworkInterface.list(type: InternetAddressType.IPv6);
    return interfaces.isNotEmpty;
  }

  static Future<void> resolveIPv6Address(HttpClient client, String baseUrl) async {
    final uri = Uri.parse(baseUrl);
    final host = uri.host;
    final port = uri.scheme == 'https' ? 443 : 80;
    try {
      final addresses = await InternetAddress.lookup(
        host,
        type: InternetAddressType.IPv6,
      );
      if (addresses.isEmpty) {
        return;
      }
      client.connectionFactory = (
          Uri uri,
          String? proxyHost,
          int? proxyPort,
          ) {
        if (uri.scheme == 'https') {
          return SecureSocket.startConnect(
              addresses.first, uri.port == 0 ? port : uri.port);
        }
        return Socket.startConnect(
            addresses.first, uri.port == 0 ? port : uri.port);
      };
    } catch (e) {
      throw Exception('Unable to setup HttpClient: $e');
    }
  }
}