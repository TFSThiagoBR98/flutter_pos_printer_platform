import 'package:flutter/services.dart';

final flutterPrinterChannel = const MethodChannel('com.sersoluciones.flutter_pos_printer_platform');
final flutterPrinterEventChannelBT = const EventChannel('com.sersoluciones.flutter_pos_printer_platform/bt_state');
final flutterPrinterEventChannelUSB = const EventChannel('com.sersoluciones.flutter_pos_printer_platform/usb_state');
final iosChannel = const MethodChannel('flutter_pos_printer_platform/methods');
final iosStateChannel = const EventChannel('flutter_pos_printer_platform/state');

enum BTStatus { none, connecting, connected, scanning, stopScanning }

enum USBStatus { none, connecting, connected }

enum TCPStatus { none, connected }

abstract class Printer {}

abstract class BasePrinterInput {}

//
abstract class PrinterConnector<T> {
  Future<bool> send(List<int> bytes);
  Future<bool> connect(T model);
  Future<bool> disconnect({int? delayMs});
}

abstract class GenericPrinter<T> extends Printer {
  PrinterConnector<T> connector;
  T model;
  GenericPrinter(this.connector, this.model) : super();

  List<int> encodeSetIP(String ip) {
    List<int> buffer = [0x1f, 0x1b, 0x1f, 0x91, 0x00, 0x49, 0x50];
    final List<String> splittedIp = ip.split('.');
    return buffer..addAll(splittedIp.map((e) => int.parse(e)).toList());
  }

  Future<bool> sendToConnector(List<int> Function() fn, {int? delayMs}) async {
    await connector.connect(model);
    final resp = await connector.send(fn());
    if (delayMs != null) {
      await Future.delayed(Duration(milliseconds: delayMs));
    }
    return resp;
  }
}
