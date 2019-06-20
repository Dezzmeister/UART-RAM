package main;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Enumeration;

import main.BigArray.Unit;
import purejavacomm.CommPortIdentifier;
import purejavacomm.PortInUseException;
import purejavacomm.SerialPort;
import purejavacomm.SerialPortEvent;
import purejavacomm.UnsupportedCommOperationException;

public class Main {
	private static boolean debug = false;
	
	private static String portName;
	private static int baudRate;
	private static BigArray memory;
	private static SerialPort port;
	private static InputStream dataIn;
	private static PrintStream dataOut;
	private static Thread senderThread;
	
	private static volatile boolean communicating = true;
	
	private static volatile boolean reading = false;
	
	/**
	 * True if the other device is currently writing to memory
	 */
	private static volatile boolean writing = false;
	
	/**
	 * Base address to read from or write to (addresses an individual byte)
	 */
	private static int baseAddress = 0;
	
	/**
	 * Number of bytes to read or written
	 */
	private static short numberOfBytes = 0;
	
	/**
	 * Current byte being read or written (address offset)
	 */
	private static short currentByte = 0;
	
	/**
	 * 6-byte instruction that will be read from the other device
	 */
	private static long infoBits = 0;
	private static int infoBytesReceived = 0;
	
	public static void main(final String[] args) {
		parseArgs(args);
		setupSerialPort();
		waitForStopCode();
	}
	
	private static void sendData() {
		while (communicating) {
			if (reading) {
				if (writing) {
					System.err.println("CRITICAL: Cannot read and write at the same time!\nStopping...");
					memory.free();
					System.exit(-1);
				} else {
					try {
						byte[] bytes = memory.getBytes(baseAddress, numberOfBytes);
						
						if (debug) {
							StringBuilder sb = new StringBuilder();
							for (byte b : bytes) {
								sb.append(Integer.toHexString(Byte.toUnsignedInt(b)));
							}
							System.out.println("READING " + sb.toString() + " FROM ADDRESS " + Integer.toHexString(baseAddress));
						}
						
						dataOut.write(bytes);
						numberOfBytes = 0;
						reading = false;
					} catch (IOException e) {
						e.printStackTrace();
						System.err.println("Failed to write to the other device!");
					}
				}
			}
		}
	}
	
	private static void waitForStopCode() {
		while (communicating);
		memory.free();
		
		try {
			dataIn.close();
			dataOut.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		port.close();
	}
	
	private static void dataAvailable(final SerialPortEvent event) {
		int in;
		try {
			while ((in = dataIn.read()) != -1) {
				System.out.println("RECEIVED " + Integer.toHexString(in));
				
				if (writing) {
					memory.putByte(baseAddress + currentByte, (byte)in);
					if (debug) System.out.println("WRITING " + Integer.toHexString(in) + " TO ADDRESS " + Integer.toHexString(baseAddress + currentByte));
					currentByte++;
					
					if (currentByte == numberOfBytes) {
						writing = false;
						currentByte = 0;
					}
				} else {
					if (!(infoBytesReceived == 0 && (((byte)in & 0xC0) >>> 6) == 0b00)) {
						if (infoBytesReceived < 6) {
							if (infoBytesReceived == 0 && (((byte)in & 0xC0) >>> 6) == 0b11) {
								System.out.println("Stop code received");
								communicating = false;
							}
							
							infoBits = (infoBits << 8) | in;
							infoBytesReceived++;
						}
						
						if (infoBytesReceived >= 6) {
							//System.out.println("Received 6 info bytes");
							infoBytesReceived = 0;
							
							int header = (int)((infoBits & 0x0000C00000000000L) >>> 46); //2 bit instruction header: either 01 (read), 10 (write), or 11 (stop) although 11 would have been detected earlier
							baseAddress = (int)((infoBits & 0x00003FFFFFFFC000L) >>> 14); //32 bit address: should be after 2 bit instruction header
							numberOfBytes = (short)(infoBits & 0x0000000000003FFFL); //14 bit value, number of bytes expected: should be after 32 bit address
							
							//System.out.println(Long.toHexString(infoBits));
							
							if (header == 0b01) {
								reading = true;
								if (debug) System.out.println("READING");
							} else if (header == 0b10) {
								writing = true;
								if (debug) System.out.println("WRITING");
							}
						}
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println("Error occurred reading data from the device, or the device was disconnected\nStopping...");
			memory.free();
			System.exit(-1);
		}
	}
	
	private static void handleSerialEvent(final SerialPortEvent event) {
		switch (event.getEventType()) {
			case SerialPortEvent.DATA_AVAILABLE:
				dataAvailable(event);
				break;
		}
	}
	
	/**
	 * Sets up the serial port with 8 data bits, 1 stop bit, and no parity bits.
	 */
	private static void setupSerialPort() {
		Enumeration<CommPortIdentifier> portIdentifiers = CommPortIdentifier.getPortIdentifiers();
		CommPortIdentifier portId = null;
		
		while (portIdentifiers.hasMoreElements()) {
			CommPortIdentifier pid = portIdentifiers.nextElement();
			if (pid.getPortType() == CommPortIdentifier.PORT_SERIAL && pid.getName().equals(portName)) {
				portId = pid;
				System.out.println("Found serial port " + portName);
				break;
			}
		}
		
		if (portId == null) {
			System.err.println("Failed to find serial port " + portName + "\nStopping...");
			memory.free();
			System.exit(-1);
		}
		
		port = null;
		try {
			port = (SerialPort) portId.open("UART-RAM", 8000);
		} catch (PortInUseException e) {
			e.printStackTrace();
			System.err.println("Port \"" + portName + "\" in use\nStopping...");
			memory.free();
			System.exit(-1);
		}
		
		try {
			port.setSerialPortParams(baudRate, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
		} catch (UnsupportedCommOperationException e) {
			e.printStackTrace();
			System.err.println("Problem setting the serial port parameters\nStopping...");
			memory.free();
			System.exit(-1);
		}
		port.notifyOnDataAvailable(true);
		port.notifyOnOutputEmpty(true);
		
		try {
			dataIn = port.getInputStream();
			port.addEventListener(Main::handleSerialEvent);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("Problem getting the InputStream and adding an event listener\nStopping...");
			memory.free();
			System.exit(-1);
		}
		
		try {
			dataOut = new PrintStream(port.getOutputStream(), true);
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println("problem getting the OutputStream\nStopping...");
			memory.free();
			System.exit(-1);
		}
		
		senderThread = new Thread(Main::sendData, "UART-RAM sender thread");
		senderThread.start();
	}
	
	private static void parseArgs(final String[] args) {
		if (args.length < 3) {
			System.err.println("You need three arguments: the port name, the baud rate, and the amount of memory to allocate!");
			System.exit(-1);
		} else {
			portName = args[0];
			baudRate = Integer.parseInt(args[1]);
			
			char c = args[2].charAt(args[2].length() - 1);
			long num = Long.parseLong(args[2].substring(0, args[2].length() - 1));
			
			switch (c) {
				case 'B':
					memory = new BigArray(num, Unit.BYTE);
					System.out.println(num + " byte(s) allocated.");
					break;
				case 'K':
					memory = new BigArray(num, Unit.KIBIBYTE);
					System.out.println(num + " kibibyte(s) allocated.");
					break;
				case 'M':
					memory = new BigArray(num, Unit.MEBIBYTE);
					System.out.println(num + " mebibyte(s) allocated.");
					break;
				case 'G':
					memory = new BigArray(num, Unit.GIBIBYTE);
					System.out.println(num + " gibibyte(s) allocated.");
					break;
					
				default:
					System.err.println("The third argument needs to be formatted correctly; see the README!");
					System.exit(-1);
			}
			
			if (args.length >= 4 && args[3].equals("-debug")) {
				debug = true;
				System.out.println("DEBUG INFO ENABLED (Warning: disable for high speed communication!)");
			}
		}
	}
}
