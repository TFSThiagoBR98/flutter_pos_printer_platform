#include "include/flutter_pos_printer_platform/flutter_pos_printer_platform_plugin_c_api.h"

#include <flutter/plugin_registrar_windows.h>

#include "flutter_pos_printer_platform_plugin.h"

void FlutterPosPrinterPlatformPluginCApiRegisterWithRegistrar(
    FlutterDesktopPluginRegistrarRef registrar) {
  flutter_pos_printer_platform::FlutterPosPrinterPlatformPlugin::RegisterWithRegistrar(
      flutter::PluginRegistrarManager::GetInstance()
          ->GetRegistrar<flutter::PluginRegistrarWindows>(registrar));
}
