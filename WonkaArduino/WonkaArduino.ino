#include <WunderbarBridge.h>

/*
	*** This example only works with the Arduino Mega/Due boards & the Arduino Mega library as it uses multiple serial communication.
	One serial communication is opened at Serial0 through the USB to allow communication between Arduino IDE and the board, while the other	is used to communicate with the bridge.

        *** Simple example that shows how to use the Wunderbar Bridge module to connect an Arduino program to the relayr open sensor cloud.
        The application uses Serial communication to send data from your laptop through the serial monitor to an Arduino board which then sends it to the bridge module which will finally
 	publish the data to the relayr open sensor cloud, where you can access it through the relayr API or SDKs. It can also receive data from the cloud back to the bridge.
        You can see the data sent/received through your serial monitor and the developer dashboard.
 */


/*  Config the bridge module with the baudrate which has to be the same used in the PC serial monitor application.
	Assumes bridge is connected to Serial1 by default.
*/

#include "RGBdriver.h"

#define CLK 22 //pins definitions for the driver        
#define DIO 24
#define REDPIN 5
#define GREENPIN 6
#define BLUEPIN 3

#define FADESPEED 5

Bridge bridge = Bridge(115200);
static bridge_payload_t rxPayload;
static uint8_t dataout[1];
int r = 0;
int g = 0;
int b = 0;
RGBdriver Driver(CLK, DIO);


void setup() {
  bridge.setBridgePort(3);
  pinMode(REDPIN, OUTPUT);
  pinMode(GREENPIN, OUTPUT);
  pinMode(BLUEPIN, OUTPUT);

  if (bridge.begin())
    Serial.print("Bridge Connected\n");
  else
    Serial.print("Bridge Not Connected\n");

  //Change the port for debugging, default is Serial0 (one used for serial monitor)
  //bridge.setDebugPort(0);
  //Change the Serial port of the bridge connection, default is Serial1
  //bridge.setBridgePort(2);
}

/* Main Loop */
void loop() {
  //when we receive new data from the cloud on the down channel
  if (bridge.newData) {
    Serial.println("Data Received!\n");
    rxPayload = bridge.getData();
    r = rxPayload.payload[0];
    g = rxPayload.payload[1];
    b = rxPayload.payload[2];

    Serial.print(r);
    if(r != 0 || g!=0 || b!=0)
    {
    Driver.begin(); // begin
    Driver.SetColor(r, g, b); //Red. first node data
    Driver.end();
    delay(50);
    }
    delay(50);
    // delay(200);
    //  analogWrite(REDPIN, r);
    //analogWrite(GREENPIN, g);
    //    analogWrite(BLUEPIN, b);int
    //delay(50);
  //  delay(100);
  }
/*  else
  {
       if (r == 0 && g == 0 && b == 0)
        {
                Driver.begin(); // begin
                Driver.SetColor(0,255, 255); //Red. first node data
                Driver.end();
        }
  }
*/


  //  Driver.begin(); // begin
  //  Driver.SetColor(0, 255, 0); //Green. first node data
  //  Driver.end();
  //  delay(500);
  //  Driver.begin(); // begin
  //  Driver.SetColor(0, 0, 255);//Blue. first node data
  //  Driver.end();
  //  delay(500);
  /*  else {
      // fade from blue to violet
      for (r = 0; r < 256; r++) {
        analogWrite(REDPIN, r);
        delay(FADESPEED);
      }
      // fade from violet to red
      for (b = 255; b > 0; b--) {
        analogWrite(BLUEPIN, b);
        delay(FADESPEED);
      }
      // fade from red to yellow
      for (g = 0; g < 256; g++) {
        analogWrite(GREENPIN, g);
        delay(FADESPEED);
      }
      // fade from yellow to green
      for (r = 255; r > 0; r--) {
        analogWrite(REDPIN, r);
        delay(FADESPEED);
      }
      // fade from green to teal
      for (b = 0; b < 256; b++) {
        analogWrite(BLUEPIN, b);
        delay(FADESPEED);
      }
      // fade from teal to blue
      for (g = 255; g > 0; g--) {
        analogWrite(GREENPIN, g);
        delay(FADESPEED);
      }
    }*/

  //On receiving data from the Serial monitor, send it to the cloud
  if (Serial.available())
  {
    char c = Serial.read();
    dataout[0] = c;
    bridge.sendData(dataout, sizeof(dataout));
  }

  //Updates the bridge module with the data it receives on the UART
  if (Serial3.available())
  {
    bridge.processSerial();
  }
}
