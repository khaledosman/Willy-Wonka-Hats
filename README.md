#Willy Wonka Hats

Willy Wonka hats is an android app built using the android's sdk bluetooth direct connection and Arduino's bridge library.

The android app searches for color sensors and bridge modules in direct connection mode and connects to them. It receives the rgb values from the color sensors via ble, prints them in the app, converts them into a byte array and sends them to
the bridge module's down channel characteristic. 

The bridge module is connected to an Arduino, the arduino program will check for data received via the bridge module, parses the array sent from the android app and sends the rgb values to a RGB LED Driver that controls an LED Strip existing inside the hat. 
