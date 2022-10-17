/*********************************************************************
This is an Arduino library for our Monochrome SHARP Memory Displays

  Pick one up today in the adafruit shop!
  ------> http://www.adafruit.com/products/1393

These displays use SPI to communicate, 3 pins are required to
interface

Adafruit invests time and resources providing this open source code,
please support Adafruit and open-source hardware by purchasing
products from Adafruit!

Written by Limor Fried/Ladyada  for Adafruit Industries.
BSD license, check license.txt for more information
All text above, and the splash screen must be included in any redistribution
*********************************************************************/

#include "ZanyDisplayLib.h"

#ifndef _swap_int16_t
#define _swap_int16_t(a, b) \
  {                         \
    int16_t t = a;          \
    a = b;                  \
    b = t;                  \
  }
#endif
#ifndef _swap_uint16_t
#define _swap_uint16_t(a, b) \
  {                          \
    uint16_t t = a;          \
    a = b;                   \
    b = t;                   \
  }
#endif

/**************************************************************************
    Sharp Memory Display Connector
    -----------------------------------------------------------------------
    Pin   Function        Notes
    ===   ==============  ===============================
      1   VIN             3.3-5.0V (into LDO supply)
      2   3V3             3.3V out
      3   GND
      4   SCLK            Serial Clock
      5   MOSI            Serial Data Input
      6   CS              Serial Chip Select
      9   EXTMODE         COM Inversion Select (Low = SW clock/serial)
      7   EXTCOMIN        External COM Inversion Signal
      8   DISP            Display On(High)/Off(Low)

 **************************************************************************/

#define TOGGLE_VCOM                                             \
  do                                                            \
  {                                                             \
    _sharpmem_vcom = _sharpmem_vcom ? 0x00 : SHARPMEM_BIT_VCOM; \
  } while (0);

/**
 * @brief Construct a new Adafruit_SharpMem object with software SPI
 *
 * @param clk The clock pin
 * @param mosi The MOSI pin
 * @param cs The display chip select pin - **NOTE** this is ACTIVE HIGH!
 * @param width The display width
 * @param height The display height
 * @param freq The SPI clock frequency desired (unlikely to be that fast in soft
 * spi mode!)
 */
ZanyDisplayLib::ZanyDisplayLib(uint8_t clk, uint8_t mosi, uint8_t cs,
                               uint16_t width, uint16_t height,
                               uint32_t freq)
    : Adafruit_GFX(width, height)
{
  _cs = cs;
  if (spidev)
  {
    delete spidev;
  }
  spidev =
      new Zany_SPIDevice(cs, clk, -1, mosi, freq, SPI_BITORDER_LSBFIRST);
}

/**
 * @brief Construct a new Adafruit_SharpMem object with hardware SPI
 *
 * @param theSPI Pointer to hardware SPI device you want to use
 * @param cs The display chip select pin - **NOTE** this is ACTIVE HIGH!
 * @param width The display width
 * @param height The display height
 * @param freq The SPI clock frequency desired
 */
ZanyDisplayLib::ZanyDisplayLib(SPIClass *theSPI, uint8_t cs,
                               uint16_t width, uint16_t height,
                               uint32_t freq)
    : Adafruit_GFX(width, height)
{
  _cs = cs;
  if (spidev)
  {
    delete spidev;
  }
  spidev = new Zany_SPIDevice(cs, freq, SPI_BITORDER_LSBFIRST, SPI_MODE0,
                              theSPI);
}

/**
 * @brief Start the driver object, setting up pins and configuring a buffer for
 * the screen contents
 *
 * @return boolean true: success false: failure
 */
boolean ZanyDisplayLib::begin(void)
{
  if (!spidev->begin())
  {
    return false;
  }
  // this display is weird in that _cs is active HIGH not LOW like every other
  // SPI device
  digitalWrite(_cs, LOW);

  // Set the vcom bit to a defined state
  _sharpmem_vcom = SHARPMEM_BIT_VCOM;

  sharpmem_buffer = (uint8_t *)malloc((WIDTH * HEIGHT) / 8);

  if (!sharpmem_buffer)
    return false;

  setRotation(0);

  return true;
}

// 1<<n is a costly operation on AVR -- table usu. smaller & faster
static const uint8_t PROGMEM set[] = {1, 2, 4, 8, 16, 32, 64, 128},
                             clr[] = {(uint8_t)~1, (uint8_t)~2, (uint8_t)~4,
                                      (uint8_t)~8, (uint8_t)~16, (uint8_t)~32,
                                      (uint8_t)~64, (uint8_t)~128};

/**************************************************************************/
/*!
    @brief Draws a single pixel in image buffer

    @param[in]  x
                The x position (0 based)
    @param[in]  y
                The y position (0 based)
    @param color The color to set:
    * **0**: Black
    * **1**: White
*/
/**************************************************************************/
void ZanyDisplayLib::drawPixel(int16_t x, int16_t y, uint16_t color)
{
  if ((x < 0) || (x >= _width) || (y < 0) || (y >= _height))
    return;

  switch (rotation)
  {
  case 1:
    _swap_int16_t(x, y);
    x = WIDTH - 1 - x;
    break;
  case 2:
    x = WIDTH - 1 - x;
    y = HEIGHT - 1 - y;
    break;
  case 3:
    _swap_int16_t(x, y);
    y = HEIGHT - 1 - y;
    break;
  }

  if (color)
  {
    sharpmem_buffer[(y * WIDTH + x) / 8] |= pgm_read_byte(&set[x & 7]);
  }
  else
  {
    sharpmem_buffer[(y * WIDTH + x) / 8] &= pgm_read_byte(&clr[x & 7]);
  }
}

/**************************************************************************/
/*!
    @brief Gets the value (1 or 0) of the specified pixel from the buffer

    @param[in]  x
                The x position (0 based)
    @param[in]  y
                The y position (0 based)

    @return     1 if the pixel is enabled, 0 if disabled
*/
/**************************************************************************/
uint8_t ZanyDisplayLib::getPixel(uint16_t x, uint16_t y)
{
  if ((x >= _width) || (y >= _height))
    return 0; // <0 test not needed, unsigned

  switch (rotation)
  {
  case 1:
    _swap_uint16_t(x, y);
    x = WIDTH - 1 - x;
    break;
  case 2:
    x = WIDTH - 1 - x;
    y = HEIGHT - 1 - y;
    break;
  case 3:
    _swap_uint16_t(x, y);
    y = HEIGHT - 1 - y;
    break;
  }

  return sharpmem_buffer[(y * WIDTH + x) / 8] & pgm_read_byte(&set[x & 7]) ? 1
                                                                           : 0;
}

/**************************************************************************/
/*!
    @brief Clears the screen
*/
/**************************************************************************/
void ZanyDisplayLib::clearDisplay()
{
  memset(sharpmem_buffer, 0xff, (WIDTH * HEIGHT) / 8);

  spidev->beginTransaction();
  // Send the clear screen command rather than doing a HW refresh (quicker)
  digitalWrite(_cs, HIGH);

  uint8_t clear_data[2] = {_sharpmem_vcom | SHARPMEM_BIT_CLEAR, 0x00};
  spidev->transfer(clear_data, 2);

  TOGGLE_VCOM;
  digitalWrite(_cs, LOW);
  spidev->endTransaction();
}

/**************************************************************************/
/*!
    @brief Renders the contents of the pixel buffer on the LCD
*/
/**************************************************************************/
void ZanyDisplayLib::refresh(int firstLine, int lastLine)
{
  uint16_t currentline;

  TOGGLE_VCOM;

  uint8_t bytes_per_line = WIDTH / 8;
  uint8_t transfer_bytes_per_line = bytes_per_line + 2;
  int number_of_lines = lastLine - firstLine + 1;
  int total_bytes_in_transaction = 1 + number_of_lines * transfer_bytes_per_line + 1;

  uint8_t data[total_bytes_in_transaction];
  data[0] = _sharpmem_vcom | SHARPMEM_BIT_WRITECMD;
  for (int j = 0; j < number_of_lines; j++)
  {
    // Send address byte
    currentline = firstLine + j + 1;
    auto current_line_offset_source = (currentline - 1) * bytes_per_line;

    auto line_number_idx = 1 + transfer_bytes_per_line * j;
    auto current_line_offset_dest = 1 + line_number_idx;
    auto trailer_idx = current_line_offset_dest + bytes_per_line;
    // Serial.printf("current_line_offset_source: %d, current_line_offset_dest: %d, trailer_idx: %d", current_line_offset_source, current_line_offset_dest, )

    data[line_number_idx] = currentline;
    // copy over this line
    memcpy(data + current_line_offset_dest, sharpmem_buffer + current_line_offset_source, bytes_per_line);
    // Send end of line
    data[trailer_idx] = 0x00;
  }

  // Send another trailing 8 bits for the last line
  data[total_bytes_in_transaction - 1] = 0x00;

  spidev->beginTransaction();
  digitalWrite(_cs, HIGH);
  spidev->transfer(data, total_bytes_in_transaction);
  digitalWrite(_cs, LOW);
  spidev->endTransaction();
}

/**************************************************************************/
/*!
    @brief Clears the display buffer without outputting to the display
*/
/**************************************************************************/
void ZanyDisplayLib::clearDisplayBuffer()
{
  memset(sharpmem_buffer, 0xff, (WIDTH * HEIGHT) / 8);
}

void ZanyDisplayLib::clearDisplayBuffer(int firstLine, int firstRow, int lastLine, int lastRow)
{
  uint8_t bytes_per_line = WIDTH / 8;
  auto non_complete_byte_rows_first = firstRow % 8;
  auto non_complete_byte_rows_last = lastRow % 8;
  firstRow += (8 - non_complete_byte_rows_first) % 8;
  lastRow -= non_complete_byte_rows_last;
  auto totalRows = lastRow - firstRow;
  auto total_row_bytes = totalRows / 8;

  for (uint16_t i = firstLine; i <= lastLine; i++)
  {
    auto current_line_offset = i * bytes_per_line;
    if (total_row_bytes > 0)
    {
      memset(sharpmem_buffer + current_line_offset + firstRow / 8, 0xff, total_row_bytes);
    }

    if (non_complete_byte_rows_first > 0)
    {
      auto byteIdx = sharpmem_buffer + current_line_offset + firstRow / 8 - 1;
      uint8_t currentValue;
      memcpy(&currentValue, byteIdx, 1);
      auto newValue = currentValue | 0xff << non_complete_byte_rows_first;
      memset(byteIdx, newValue, 1);
    }

    if (non_complete_byte_rows_last > 0)
    {
      auto byteIdx = sharpmem_buffer + current_line_offset + firstRow / 8 + total_row_bytes;
      uint8_t currentValue;
      memcpy(&currentValue, byteIdx, 1);
      auto newValue = currentValue | 0xff >> (8 - non_complete_byte_rows_last);
      memset(byteIdx, newValue, 1);
    }
  }
}
