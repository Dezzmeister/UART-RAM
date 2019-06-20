# UART-RAM
Use your computer's RAM with an FPGA or other device over a serial port


The jar accepts three parameters:

  First parameter: Serial port name
  
  Second parameter: Baud rate
  
  Third parameter: Amount of memory to allocate, with a one-letter suffix to indicate the unit:
  
    100B - 100 bytes (8 bits each)
    
    500K - 500 kibibytes
    
    250M - 250 mebibytes
    
    2G - 2 gibibytes
    
    You may need to start the application with more memory using the -Xmx JVM argument
    
  Fourth parameter (optional): -debug
  
Debug mode should be disabled for high speed communication to prevent calls to System.out.println() from slowing down the application.


The application accepts 6-byte instructions through the serial port. The instructions have a 2-bit opcode header, followed by a 32-bit address and 14 bits indicating the number of bytes to read/write.

2-bit opcodes:

  01 - read from memory
  
  10 - write to memory
  
  11 - stop the application
  

Example instruction (hex):

80 00 00 00 40 05

This indicates that the device will write 5 bytes starting at address 1. After sending this, the device would send the five bytes.


Another example instruction:

40 00 00 03 C0 FF

This indicates that the device will read 255 bytes starting at address 15. After sending this, the application will send the 255 bytes.

