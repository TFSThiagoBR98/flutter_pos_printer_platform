#ifndef FLUTTER_PLUGIN_FLUTTER_POS_PRINTER_PLATFORM_PLUGIN_H_
#define FLUTTER_PLUGIN_FLUTTER_POS_PRINTER_PLATFORM_PLUGIN_H_

#include <flutter/method_channel.h>
#include <flutter/plugin_registrar_windows.h>

#include <memory>

namespace flutter_pos_printer_platform {

class FlutterPosPrinterPlatformPlugin : public flutter::Plugin {
 public:
  static void RegisterWithRegistrar(flutter::PluginRegistrarWindows *registrar);

  FlutterPosPrinterPlatformPlugin();

  virtual ~FlutterPosPrinterPlatformPlugin();

  // Disallow copy and assign.
  FlutterPosPrinterPlatformPlugin(const FlutterPosPrinterPlatformPlugin&) = delete;
  FlutterPosPrinterPlatformPlugin& operator=(const FlutterPosPrinterPlatformPlugin&) = delete;

  // Called when a method is called on this plugin's channel from Dart.
  void HandleMethodCall(
      const flutter::MethodCall<flutter::EncodableValue> &method_call,
      std::unique_ptr<flutter::MethodResult<flutter::EncodableValue>> result);
};

}  // namespace flutter_pos_printer_platform

#endif  // FLUTTER_PLUGIN_FLUTTER_POS_PRINTER_PLATFORM_PLUGIN_H_
