import Flutter
import UIKit
import CoreBluetooth

enum BtStates {
    static let disconnected = 0
    static let connecting = 1
    static let connected = 2
}

enum ChannelName {
    static let channel = "flutter_pos_printer_platform/methods"
    static let event = "flutter_pos_printer_platform/state"
}

public class FlutterPosPrinterPlatformPlugin: NSObject, FlutterPlugin, FlutterStreamHandler, CBCentralManagerDelegate, CBPeripheralDelegate {
    let channel: FlutterMethodChannel
    private var eventSink: FlutterEventSink?
    private var centralManager: CBCentralManager!
    private var peripheral: CBPeripheral!
    private var printerCharacteristic: CBCharacteristic!
    private var devices: [CBPeripheral] = [CBPeripheral]()
    
    // we need to init this plugin to get the instance of centralmanager
    init (_ channel: FlutterMethodChannel, registrar: FlutterPluginRegistrar) {
        self.channel = channel
        super.init()
        // In Swift, this is done in viewDidLoad()
        centralManager = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: false])
        
        let eventChannel = FlutterEventChannel(name: ChannelName.event,
                                               binaryMessenger: registrar.messenger())
        eventChannel.setStreamHandler(self)
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: ChannelName.channel, binaryMessenger: registrar.messenger())
        let instance = FlutterPosPrinterPlatformPlugin(channel, registrar: registrar)
        
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        self.eventSink = events
        return nil
    }
    
    public func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "getBluetoothState":
            getBluetoothState(result: result)
        case "startScan":
            if(self.centralManager.state == .poweredOn) {
                print("Scanning...")
                devices.removeAll()
                centralManager?.scanForPeripherals(withServices: nil, options: nil)
                result(nil)
            } else {
                createDialog(message: "Please turn the Bluetooth on")
                result(false);
            }
        case "stopScan":
            if(self.centralManager.state == .poweredOn) {
                print("Stop scan")
                centralManager?.stopScan()
            }
            result(nil)
        case "connect":
            if(self.centralManager.state == .poweredOn) {
                if let args = call.arguments as? Dictionary<String, Any>,
                   let name = args["name"] as? String {
                    let address = args["address"] as? String
                    // please check the "as" above  - wasn't able to test
                    // handle the method
                    connectDevice(name: name, address: address!, result: result)
                    
                } else {
                    result(FlutterError.init(code: "errorSetDebug", message: "data or format error", details: nil))
                }
            } else {
                createDialog(message: "Please turn the Bluetooth on")
                result(false);
            }
        case "disconnect":
            if (self.peripheral == nil) {
                result(false)
            }
            else {
                centralManager?.cancelPeripheralConnection(self.peripheral)
                result(true)
            }
        case "writeData":
            if let args = call.arguments as? Dictionary<String, Any>,
               let bytes: [UInt8] = args["bytes"] as? [UInt8] {
                //                let length = args["length"] as? Int
                sendData(bytes: bytes)
                result(nil)
            } else {
                result(FlutterError.init(code: "errorSetDebug", message: "data or format error", details: nil))
            }
            result(nil)
        case "getPlatformVersion":
            result("iOS " + UIDevice.current.systemVersion)
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    func createDialog(message: String){
        DispatchQueue.main.async {
            let alert = UIAlertController(title: "Bluetooth Settings", message: message, preferredStyle: .alert);
            alert.addAction(UIAlertAction(title: "OK", style: UIAlertAction.Style.default, handler: nil))
            UIApplication.shared.keyWindow?.rootViewController?.present(alert, animated: true, completion: nil);
        }
    }
    
    func connectDevice(name: String, address: String, result: FlutterResult){
        
        //stopScan
        centralManager.stopScan()
        
        
        if let peripheral = devices.first(where:{$0.identifier.uuidString == address})
        {
            decodePeripheralState(peripheralState: peripheral.state)
            if (peripheral.state != .connected){
                // connecting ...
                // sendStateEvent(state: BtStates.connecting)
                //connect
                centralManager.connect(peripheral, options: nil)
                self.peripheral = peripheral
            }else if (peripheral.state == .connected){
                sendStateEvent(state: BtStates.connected)
            }
            result(true)
        }else{
            result(false)
        }
        
    }
    
    private func getBluetoothState(result: FlutterResult) {
        if(self.centralManager.state == .poweredOn) {
            result(true);
        } else {
            result(false);
        }
    }
    
    public func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        //discover all service
        // Nil it’s going to discover all services
        peripheral.discoverServices(nil)
        peripheral.delegate = self
        print(" ==== Connected ==== ")
        
    }
    
    public func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        sendStateEvent(state: BtStates.disconnected)
        print(" ==== Disconnected ==== ")
    }
    
    
    public func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {
        guard peripheral.name != nil else {return}
        
        if (peripheral.name != nil && !devices.contains(where: {$0.identifier.uuidString == peripheral.identifier.uuidString})){
            
            print("bt devices found: " + (peripheral.name ?? ""))
            
            let deviceMap = ["name":peripheral.name, "address": peripheral.identifier.uuidString]
            
            devices.append(peripheral)
            self.channel.invokeMethod("ScanResult", arguments: deviceMap)
        }
    }
    
    public func centralManagerDidUpdateState(_ central: CBCentralManager) {
        switch central.state {
        case .poweredOn:
            
            print(" ==== BT ON ==== ")
            break
        case .poweredOff:
            print(" ==== BT OFF ==== ")
            sendStateEvent(state: BtStates.disconnected)
            // Alert user to turn on Bluetooth
            break
        case .resetting:
            
            // Wait for next state update and consider logging interruption of Bluetooth service
            break
        case .unauthorized:
            createDialog(message: "Enable Bluetooth permission in app Settings")
            // Alert user to enable Bluetooth permission in app Settings
            break
        case .unsupported:
            createDialog(message: "This device does not support Bluetooth")
            // Alert user their device does not support Bluetooth and app will not work as expected
            break
        case .unknown:
            break
        default:
            
            // Wait for next state update
            break
        }
    }
    
    public func centralManager(_ central: CBCentralManager, connectionEventDidOccur event: CBConnectionEvent, for peripheral: CBPeripheral) {
        
        switch event {
        case .peerConnected:
            sendStateEvent(state: BtStates.connected)
        case .peerDisconnected:
            sendStateEvent(state: BtStates.disconnected)
        default:
            break
        }
    }
    
    public func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        print("peripheral: %@ failed to connect", peripheral)
        sendStateEvent(state: BtStates.disconnected)
    }
    
    
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        
        if let services = peripheral.services {
            
            //discover characteristics of services
            for service in services {
                
                // passing nil it's going to discover all characteristics.
                peripheral.discoverCharacteristics(nil, for: service)
            }
        }
    }
    
    public func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if (service.uuid.uuidString == "18F0" || service.uuid.uuidString == "18F1"){
            //            print("\n -------- SERVICE: " + service.description)
            
            if let charac = service.characteristics {
                for characteristic in charac {
                    
                    if characteristic.uuid.uuidString == "2AF1" {
                        //                        print("characteristic: " + characteristic.description )
                        self.printerCharacteristic = characteristic
                        sendStateEvent(state: BtStates.connected)
                    }
                }
            }
            
        }
    }
    
    func decodePeripheralState(peripheralState: CBPeripheralState) {
        
        switch peripheralState {
        case .disconnected:
            print("Peripheral state: disconnected")
        case .connected:
            print("Peripheral state: connected")
        case .connecting:
            print("Peripheral state: connecting")
        case .disconnecting:
            print("Peripheral state: disconnecting")
        default:
            break
        }
        
    } // END func decodePeripheralState(peripheralState
    
    func printTestData(characteristic: CBCharacteristic) {
        if let descriptors = characteristic.descriptors {
            for descriptor in descriptors {
                print("descriptor: " + descriptor.description )
            }
        }
        
        let dataToSend = "Print Success!: " + characteristic.uuid.uuidString
        let buf: [UInt8] = Array(dataToSend.utf8)
        let data = Data(buf)
        
        let clearCommand: [UInt8] = [0x1B, 0x40]
        self.peripheral?.writeValue(Data(clearCommand), for: characteristic, type: .withResponse) // clear
        self.peripheral?.writeValue(Data([0x1B, 0x61, 0x01]), for: characteristic, type: .withResponse) // center
        self.peripheral?.writeValue(data, for: characteristic, type: .withResponse)
        self.peripheral?.writeValue(Data([0x0A]), for: characteristic, type: .withResponse) // Line Feed x 1
        
    }
    
    private func sendData(bytes: [UInt8]){
        if (self.centralManager.state != .poweredOn) { return }
        self.peripheral?.writeValue(Data([0x1B, 0x40]), for: self.printerCharacteristic , type: .withResponse) // clear
        self.peripheral?.writeValue(Data(bytes), for: self.printerCharacteristic , type: .withResponse)
    }
    
    
    private func sendStateEvent(state :Int) {
        guard let eventSink = self.eventSink else {
            return
        }
        eventSink(state)
        // eventSink(FlutterError(code: "unavailable",
        //  message: "Charging status unavailable",
        //  details: nil))
    }
}
