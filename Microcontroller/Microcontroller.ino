/*********************************************************************
  This is an example for our nRF52 based Bluefruit LE modules
  Pick one up today in the adafruit shop!
  Adafruit invests time and resources providing this open source code,
  please support Adafruit and open-source hardware by purchasing
  products from Adafruit!
  MIT license, check LICENSE for more information
  All text above, and the splash screen below must be included in
  any redistribution
*********************************************************************/

/* This sketch implement part of Nordic custom LED Button Service (LBS).
   Install "nRF Blinky" app on your Android/iOS to control the on-board LED
   - https://apps.apple.com/us/app/nrf-blinky/id1325014347
   - https://play.google.com/store/apps/details?id=no.nordicsemi.android.nrfblinky
*/
#define DBG_ADVERTISE_AND_REFRESH false

#include <TimeLib.h>
#include <bluefruit.h>
#include <Adafruit_GFX.h>
#include "ZanyDisplayLib.h"
#include <Fonts/FreeMono9pt7b.h>
#include <Fonts/FreeMonoBold9pt7b.h>
#include <Fonts/FreeMono12pt7b.h>
#include <Fonts/FreeMonoBold12pt7b.h>
#include <Fonts/FreeMono18pt7b.h>
#include <Fonts/FreeMonoBold18pt7b.h>
#include <Fonts/FreeMono24pt7b.h>
#include <Fonts/FreeMonoBold24pt7b.h>

// any pins can be used
#define SHARP_SCK 9
#define SHARP_MOSI 8
#define SHARP_SS 7

// Set the size of the display here, e.g. 144x168!
ZanyDisplayLib display(SHARP_SCK, SHARP_MOSI, SHARP_SS, 144, 168, 8000000);
// The currently-available SHARP Memory Display (144x168 pixels)
// requires > 4K of microcontroller RAM; it WILL NOT WORK on Arduino Uno
// or other <4K "classic" devices!  The original display (96x96 pixels)
// does work there, but is no longer produced.

#define BLACK 0
#define WHITE 1
bool fullRedraw = true;
time_t currentTime = now();
unsigned long steps = 0;
bool isConnected = false;

// Battery Logic
const int maxBatteryVoltageSampleCount = 60;
int batteryVoltageSamples[maxBatteryVoltageSampleCount];
int currentVoltageSampleIdx = 0;
int currentBatteryVoltageSampleCount = 0;
const int lutVoltage[] = {3270, 3610, 3690, 3710, 3730, 3750, 3770, 3790, 3800, 3820, 3840, 3850, 3870, 3910, 3950, 3980, 4020, 4080, 4110, 4150, 4200};

int minorHalfSize; // 1/2 of lesser of display width or height

/* LBS Service: 00001523-1212-EFDE-1523-785FEABCD123
   LBS Button : 00001524-1212-EFDE-1523-785FEABCD123
   LBS LED    : 00001525-1212-EFDE-1523-785FEABCD123
*/

const uint8_t LBS_UUID_SERVICE[] =
    {
        0x23, 0xD1, 0xBC, 0xEA, 0x5F, 0x78, 0x23, 0x15,
        0xDE, 0xEF, 0x12, 0x12, 0x23, 0x15, 0x00, 0x00};

const uint8_t LBS_UUID_CHR_BUTTON[] =
    {
        0x23, 0xD1, 0xBC, 0xEA, 0x5F, 0x78, 0x23, 0x15,
        0xDE, 0xEF, 0x12, 0x12, 0x24, 0x15, 0x00, 0x00};

const uint8_t LBS_UUID_CHR_LED[] =
    {
        0x23, 0xD1, 0xBC, 0xEA, 0x5F, 0x78, 0x23, 0x15,
        0xDE, 0xEF, 0x12, 0x12, 0x25, 0x15, 0x00, 0x00};

#if DBG_ADVERTISE_AND_REFRESH
BLEUuid serviceUUID("2b12b859-1407-41b4-977b-9174e0914301");
BLEUuid charUUID("e182417c-a449-47ce-bf93-0d9c07e68f02");
#else
BLEUuid serviceUUID("d9d919ee-b681-4a63-9b2d-9fb22fc56b3b");
BLEUuid charUUID("f681631f-f2d3-42e2-b769-ab1ef3011029");
#endif

BLEService lbs(serviceUUID);
BLECharacteristic lsbLED(charUUID);

// Use on-board button if available, else use A0 pin
#ifdef PIN_BUTTON1
uint8_t button = PIN_BUTTON1;
#else
uint8_t button = A0;
#endif

uint8_t buttonState;

void setup()
{
  // Serial.begin(115200);
  // while(!Serial);
  // pinMode(D6, INPUT_PULLDOWN);
  // pinMode(D2, INPUT_PULLDOWN);

  disableLEDs();
  setupBattery();
  NRF_NFCT->TASKS_DISABLE = 1;
  setupDisplay();
  drawDisplay();
  setupBluetooth();
}

void loop()
{
  // Serial.printf("%d, %d\n", digitalRead(D6),  digitalRead(D2));
  currentTime++;
  fullRedraw = second(currentTime) == 0;
  addBatteryVoltageSampleToBuffer();
  drawDisplay();
  delay(998);
}

void setupDisplay()
{
  display.begin();
  display.clearDisplay();
  display.setTextColor(BLACK);
  display.cp437(true);
  display.setRotation(0);
}

void setupBluetooth()
{
  // Initialize Bluefruit with max concurrent connections as Peripheral = MAX_PRPH_CONNECTION, Central = 0
  Bluefruit.begin();
#if DBG_ADVERTISE_AND_REFRESH
  Bluefruit.setName("DumbWatchDBG");
#else
  Bluefruit.setName("DumbWatch");
#endif
  Bluefruit.Periph.setConnectCallback(connect_callback);
  Bluefruit.Periph.setDisconnectCallback(disconnect_callback);
  Bluefruit.Periph.setConnIntervalMS(50, 4000);
  Bluefruit.autoConnLed(false);
  // Bluefruit.setTxPower(-4);

  // Note: You must call .begin() on the BLEService before calling .begin() on
  // any characteristic(s) within that service definition.. Calling .begin() on
  // a BLECharacteristic will cause it to be added to the last BLEService that
  // was 'begin()'ed!
  lbs.begin();
  // Configure the LED characteristic
  // Properties = Read + Write
  // Permission = Open to read, Open to write
  // Fixed Len  = 1 (LED state)
  lsbLED.setProperties(CHR_PROPS_READ | CHR_PROPS_WRITE);
  lsbLED.setPermission(SECMODE_OPEN, SECMODE_OPEN);
  lsbLED.setFixedLen(7);
  lsbLED.begin();
  lsbLED.write8(0x00); // led = off

  lsbLED.setWriteCallback(led_write_callback);

  // Advertising packet
  Bluefruit._stopConnLed();
  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addTxPower();

  // Include HRM Service UUID
  Bluefruit.Advertising.addService(lbs);

  // Secondary Scan Response packet (optional)
  // Since there is no room for 'Name' in Advertising packet
  Bluefruit.ScanResponse.addName();

  /* Start Advertising
     - Enable auto advertising if disconnected
     - Interval:  fast mode = 20 ms, slow mode = 152.5 ms
     - Timeout for fast mode is 30 seconds
     - Start(timeout) with timeout = 0 will advertise forever (until connected)

     For recommended advertising interval
     https://developer.apple.com/library/content/qa/qa1931/_index.html
  */
  Bluefruit.Advertising.restartOnDisconnect(true);
#if DBG_ADVERTISE_AND_REFRESH
  Bluefruit.Advertising.setInterval(244, 244); // in unit of 0.625 ms
#else
  Bluefruit.Advertising.setInterval(244 * 6, 244 * 6); // in unit of 0.625 ms
#endif
  Bluefruit.Advertising.setFastTimeout(1); // number of seconds in fast mode
  Bluefruit.Advertising.start(0);          // 0 = Don't stop advertising after n seconds
}

void setupBattery()
{
  // Enable reading of battery voltage
  pinMode(VBAT_ENABLE, OUTPUT);
  pinMode(PIN_VBAT, INPUT);

  // Charge indicator?
  pinMode(17, INPUT);

  digitalWrite(VBAT_ENABLE, LOW);

  // initialize ADC 2.4V/4096
  analogReference(AR_INTERNAL_2_4); // Vref=2.4V
  analogReadResolution(12);         // 4096
}

void disableLEDs()
{
  pinMode(LED_BLUE, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(LED_RED, OUTPUT);

  digitalWrite(LED_BLUE, LED_STATE_ON);  // led off
  digitalWrite(LED_GREEN, LED_STATE_ON); // led off
  digitalWrite(LED_RED, LED_STATE_ON);   // led off
}

void addBatteryVoltageSampleToBuffer()
{
  auto currentVoltage = analogRead(PIN_VBAT);
  batteryVoltageSamples[currentVoltageSampleIdx] = currentVoltage;
  if (currentBatteryVoltageSampleCount < maxBatteryVoltageSampleCount)
    currentBatteryVoltageSampleCount++;
  currentVoltageSampleIdx++;
  currentVoltageSampleIdx %= maxBatteryVoltageSampleCount;
}

double readAverageBatteryVoltageInMillivoltsFromBuffer()
{
  double analogSampleSum = 0;
  for (int i = 0; i < currentBatteryVoltageSampleCount; i++)
  {
    analogSampleSum += batteryVoltageSamples[i];
  }

  auto averageAnalogSample = analogSampleSum / currentBatteryVoltageSampleCount;
  return averageAnalogSample * 1.84525 - 130.8;
}

double getPercentageFromBatteryVoltage(double voltageInMillivolts)
{
  for (int i = 0; i < 21; i++)
  {
    if (lutVoltage[i] > voltageInMillivolts)
    {
      if (i == 0)
        return 0.0;

      auto lowerVoltageBound = lutVoltage[i - 1];
      auto upperVoltageBound = lutVoltage[i];
      auto lowerPercentageBound = (i - 1) * 5;
      auto upperPercentageBound = i * 5;
      auto factor = (voltageInMillivolts - lowerVoltageBound) / (upperVoltageBound - lowerVoltageBound);
      return (lowerPercentageBound * (1 - factor) + upperPercentageBound * factor);
    }
  }

  return 100.0;
}

void drawDisplay()
{
#if DBG_ADVERTISE_AND_REFRESH
  fullRedraw = true; // TODO: only added for testing purposes
#endif

  // helper coordinates
  int timeSectorHeightPx = 65;
  float leftSectorWidthRelative = 0.56;
  int verticalMidlinePx = display.height() / 2;

  if (!fullRedraw)
    // display.clearDisplayBuffer(display.height() - 37, display.width() * leftSectorWidthRelative + 15, display.height() - 15, display.width() * leftSectorWidthRelative + 52);
    display.clearDisplayBuffer(display.height() - 37, 0, display.height() - 15, display.width() * leftSectorWidthRelative + 52);
  else
    display.clearDisplayBuffer();

  // Draw Time (Seconds)
  char secondsStr[5];
  sprintf(secondsStr, "%02d", second(currentTime));
  display.setCursor(display.width() * leftSectorWidthRelative + 12, display.height() - 15);
  display.setFont(&FreeMonoBold18pt7b);
  display.write(secondsStr);

  // Draw connection state
  char connectionStateStr[5];
  sprintf(connectionStateStr, isConnected ? "Yes" : "No");
  display.setCursor(5, display.height() - 15);
  display.setFont(&FreeMonoBold18pt7b);
  display.write(connectionStateStr);

  // Draw separator lines
  display.drawLine(0, verticalMidlinePx - timeSectorHeightPx / 2, display.width(), verticalMidlinePx - timeSectorHeightPx / 2, BLACK);
  display.drawLine(0, verticalMidlinePx + timeSectorHeightPx / 2, display.width(), verticalMidlinePx + timeSectorHeightPx / 2, BLACK);
  display.drawLine(display.width() * leftSectorWidthRelative, 0, display.width() * leftSectorWidthRelative, verticalMidlinePx - timeSectorHeightPx / 2, BLACK);
  display.drawLine(display.width() * leftSectorWidthRelative, verticalMidlinePx + timeSectorHeightPx / 2, display.width() * leftSectorWidthRelative, display.height(), BLACK);

  if (fullRedraw)
  {
    // Time (Hours and Minutes)
    char timeStr[9];
    sprintf(timeStr, "%02d:%02d", hour(currentTime), minute(currentTime));
    display.setFont(&FreeMonoBold24pt7b);
    display.setCursor(5, verticalMidlinePx + timeSectorHeightPx / 2 - 25);
    display.write(timeStr);

    // Date
    char dateStr[12];
    sprintf(dateStr, "%s, %02d.%02d.%02d", getWeekday(currentTime), day(currentTime), month(currentTime), year(currentTime) - 2000);
    display.setFont(&FreeMonoBold9pt7b);
    display.setCursor(4, verticalMidlinePx + timeSectorHeightPx / 2 - 5);

    display.write(dateStr);

    // Steps
    char stepsStr[9];
    sprintf(stepsStr, "%d", steps);
    display.setFont(&FreeMonoBold12pt7b);
    display.setCursor(4, 20);
    display.write("Steps");
    display.setCursor(4, 43);
    display.write(stepsStr);

    // Battery
    auto voltageMs = readAverageBatteryVoltageInMillivoltsFromBuffer();
    auto percentage = getPercentageFromBatteryVoltage(voltageMs);
    char battStr[4];
    sprintf(battStr, "%.0f%%", percentage);
    display.setFont(&FreeMonoBold12pt7b);
    display.setCursor(display.width() * leftSectorWidthRelative + 4, 20);
    display.write("Batt");
    display.setCursor(display.width() * leftSectorWidthRelative + 4, 43);
    display.write(battStr);

    // Refresh full display
    display.refresh(0, 167);
    fullRedraw = false; // Only redraw part of the screen next cycle
  }
  else
  {
    display.refresh(display.height() - 37, display.height() - 15);
  }
}

void led_write_callback(uint16_t conn_hdl, BLECharacteristic *chr, uint8_t *data, uint16_t len)
{
  digitalWrite(LED_RED, LED_STATE_ON); // led off, for weird behavior when after first bonding led remains red
  unsigned long unixEpochSeconds =
      (unsigned long)data[0] + ((unsigned long)data[1] << (8 * 1)) + ((unsigned long)data[2] << (8 * 2)) + ((unsigned long)data[3] << (8 * 3));

  steps = (unsigned long)data[4] + ((unsigned long)data[5] << (8 * 1)) + ((unsigned long)data[6] << (8 * 2));
  currentTime = (time_t)unixEpochSeconds;
}

// callback invoked when central connects
void connect_callback(uint16_t conn_handle)
{
  (void)conn_handle;
  BLEConnection *conn = Bluefruit.Connection(conn_handle);
  conn->requestConnectionParameter(90, 2, 500);
  isConnected = true;

  Serial.println("Connected");
}

/**
   Callback invoked when a connection is dropped
   @param conn_handle connection where this event happens
   @param reason is a BLE_HCI_STATUS_CODE which can be found in ble_hci.h
*/
void disconnect_callback(uint16_t conn_handle, uint8_t reason)
{
  (void)conn_handle;
  (void)reason;

  isConnected = false;
  Serial.println();
  Serial.print("Disconnected, reason = 0x");
  Serial.println(reason, HEX);
}

const char *getWeekday(time_t time)
{
  switch (weekday(time))
  {
  case 1:
    return "SO";
  case 2:
    return "MO";
  case 3:
    return "DI";
  case 4:
    return "MI";
  case 5:
    return "DO";
  case 6:
    return "FR";
  case 7:
    return "SA";
  }

  return "??";
}