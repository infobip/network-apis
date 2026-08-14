import 'dart:io';

import 'package:infobip_mi_demo_app/api/cellular_http_verify_client.dart';
import 'package:infobip_mi_demo_app/api/models/cellular_switch_result.dart';
import 'package:infobip_mi_demo_app/api/models/verify_api_response.dart';
import 'package:infobip_mi_demo_app/api/models/verify_enterprise_response.dart';
import 'package:infobip_mi_demo_app/api/models/verify_exception.dart';
import 'package:infobip_mi_demo_app/api/models/verify_result.dart';
import 'package:infobip_mi_demo_app/config/app_config.dart';
import 'package:infobip_mi_demo_app/services/native_invocation.dart';

import '../utils/ipv6_utils.dart';
import '../utils/json_converter.dart';

class AndroidHttpVerifyClient implements CellularHttpVerifyClient {
  @override
  Future<VerifyResult> sendVerifyRequest(
      String msisdn, String apiKey, String locationCode) async {
    final client = HttpClient()
      ..connectionTimeout = AppConfiguration.requestTimeout;

    try {
      final switchToCellularResult =
          CellularSwitchResult.fromName(await switchToCellularNetwork());
      if (switchToCellularResult != CellularSwitchResult.success) {
        return VerifyResult(cellularSwitchResult: switchToCellularResult);
      }

      if (await IPv6Utils.checkIfDeviceIsOnIpv6Network()) {
        await IPv6Utils.resolveIPv6Address(client, AppConfiguration.backendApiBaseUrl);
      }

      final token =
          await _sendVerifyRequest(client, msisdn, apiKey, locationCode);

      final resetNetworkResult = await resetToDefaultNetwork();
      if (resetNetworkResult == null || resetNetworkResult == false) {
        return VerifyResult(
            cellularSwitchResult: CellularSwitchResult.failNetworkReset);
      }

      final resultResponse = await _pollForResult(client, token);

      return VerifyResult(
          verifyApiResponse:
              VerifyApiResponse.fromJson(await toJson(resultResponse)));
    } catch (e) {
      rethrow;
    } finally {
      client.close();
    }
  }

  Future<String> _sendVerifyRequest(HttpClient client, String msisdn,
      String apiKey, String locationCode) async {
    final backendResponse =
        await _sendBackendVerifyRequest(client, msisdn, apiKey, locationCode);

    final verifyEnterpriseResponse =
        VerifyEnterpriseResponse.fromJson(await toJson(backendResponse));

    if (backendResponse.statusCode != HttpStatus.ok) {
      throw VerifyException(verifyEnterpriseResponse.errorDescription!,
          verifyEnterpriseResponse.token);
    }

    if (verifyEnterpriseResponse.deviceRedirectUrl != null) {
      HttpClientRequest redirectRequest = await client
          .getUrl(Uri.parse(verifyEnterpriseResponse.deviceRedirectUrl!));
      redirectRequest.followRedirects = false;

      // final redirect response will be from Infobip or the defined redirectUrl
      await _followRedirects(
          client,
          await redirectRequest
              .close()
              .timeout(AppConfiguration.requestTimeout));
    }

    return verifyEnterpriseResponse.token!;
  }

  Future<HttpClientResponse> _sendBackendVerifyRequest(HttpClient client,
      String msisdn, String apiKey, String locationCode) async {
    HttpClientRequest enterpriseRequest = await client.getUrl(
        Uri.parse('${AppConfiguration.backendApiBaseUrl}/verify/$msisdn'));
    enterpriseRequest.headers.set('apiKey', apiKey);
    enterpriseRequest.headers.set('locationCode', locationCode);
    return await enterpriseRequest
        .close()
        .timeout(AppConfiguration.requestTimeout);
  }

  Future<HttpClientResponse> _followRedirects(
      HttpClient client, HttpClientResponse response) async {
    HttpClientResponse redirectResponse = response;
    if (response.isRedirect) {
      var redirectUri =
          Uri.parse(response.headers.value(HttpHeaders.locationHeader)!);
      HttpClientRequest redirectRequest = await client.getUrl(redirectUri);
      redirectRequest.cookies.addAll(response.cookies);
      redirectRequest.followRedirects = false;

      redirectResponse = await _followRedirects(
          client,
          await redirectRequest
              .close()
              .timeout(AppConfiguration.requestTimeout));
    }
    return redirectResponse;
  }

  Future<HttpClientResponse> _pollForResult(
      HttpClient client, String token) async {
    for (int i = 0; i < AppConfiguration.pollingRetryLimit; i++) {
      var checkRequest = await client.getUrl(Uri.parse(
          '${AppConfiguration.backendApiBaseUrl}/verify/result/$token'));
      var checkResponse =
          await checkRequest.close().timeout(AppConfiguration.requestTimeout);

      if (checkResponse.statusCode == HttpStatus.ok) {
        return checkResponse;
      } else if (checkResponse.statusCode == HttpStatus.noContent) {
        await Future.delayed(AppConfiguration.pollingRetryInterval);
      } else {
        break;
      }
    }
    throw Exception('Failed to fetch callback result');
  }
}
