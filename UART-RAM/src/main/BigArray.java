package main;

import java.lang.reflect.Field;

import sun.misc.Unsafe;

/**
 * An array that can be longer than ({@link Integer#MAX_VALUE} * 4) bytes. <br>
 * It is important that you call {@link BigArray#free()} when you are done with the <code>BigArray</code>. 
 *
 * @author Joe Desmond
 */
public class BigArray {
	private static final Unsafe unsafe;
	
	static {
		Unsafe tempUnsafe = null;
		
		try {
			Field theUnsafeField = Unsafe.class.getDeclaredField("theUnsafe");
			theUnsafeField.setAccessible(true);
			tempUnsafe = (Unsafe) theUnsafeField.get(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		unsafe = tempUnsafe;
		
		if (unsafe == null) {
			System.err.println("Unable to obtain instance of sun.misc.Unsafe!\nStopping...");
			System.exit(-1);
		}
	}
	
	public enum Unit {
		/**
		 * 8 bits
		 */
		BYTE,
		/**
		 * 1024 bytes, or 1 KiB
		 */
		KIBIBYTE,
		/**
		 * 1024 KiB, or 1 MiB
		 */
		MEBIBYTE,
		/**
		 * 1024 MiB, or 1 GiB
		 */
		GIBIBYTE
	}
	
	/**
	 * Length of the array in bytes
	 */
	private final long byteLength;
	/**
	 * Starting address of the array
	 */
	private final long address;
	
	/**
	 * Creates a BigArray of the specified length and unit. If <code>unit</code> is null,
	 * <code>length</code> will be the length of the array in bytes.
	 * <p>
	 * This constructor allocates memory that needs to be cleaned up with {@link BigArray#free()}.
	 * 
	 * @param length length of the array
	 * @param unit unit of <code>length</code>
	 */
	public BigArray(long length, Unit unit) {
		switch (unit) {
			case BYTE:
				byteLength = length;
				break;
				
			case KIBIBYTE:
				byteLength = length * 1024;
				break;
				
			case MEBIBYTE:
				byteLength = length * 1024 * 1024;
				break;
				
			case GIBIBYTE:
				byteLength = length * 1024 * 1024 * 1024;
				break;
			
			default:
				byteLength = length;
		}
		
		long tempAddress = 0;
		
		try {
			tempAddress = unsafe.allocateMemory(byteLength);
		} catch (OutOfMemoryError e) {
			e.printStackTrace();
			System.err.println("Allocate more memory to the JVM!\nStopping...");
			System.exit(-1);
		}
		
		address = tempAddress;
	}
	
	/**
	 * Puts a byte at the specified offset from the start of the array.
	 * 
	 * @param byteOffset offset, in bytes
	 * @param in byte to be stored
	 */
	public void putByte(long byteOffset, byte in) {
		if (byteOffset < byteLength && byteOffset >= 0) {
			unsafe.putByte(address + byteOffset, in);
		} else if (byteOffset < 0) {
			System.err.println("Attempted to write to a memory address outside of the array! (" + byteOffset + " < 0)");
		} else {
			System.err.println("Attempted to write to a memory address outside of the array! (" + byteOffset + " >= " + byteLength + ")");
		}
	}
	
	/**
	 * Reads a byte at the specified offset from the start of the array. Returns 0 if there is an error.
	 * 
	 * @param byteOffset offset, in bytes
	 * @return byte at the specified offset
	 */
	public byte getByte(long byteOffset) {
		if (byteOffset < byteLength && byteOffset >= 0) {
			return unsafe.getByte(address + byteOffset);
		} else if (byteOffset < 0) {
			System.err.println("Attempted to read from a memory address outside of the array! (" + byteOffset + " < 0)");
		} else {
			System.err.println("Attempted to read from a memory address outside of the array! (" + byteOffset + " >= " + byteLength + ")");
		}
		
		return 0;
	}
	
	/**
	 * Returns a series of bytes.
	 * 
	 * @param byteOffset address to start reading from
	 * @param numberOfBytes number of bytes to read
	 * @return a byte[] with length <code>numberOfBytes</code>, or length 0 if <code>byteOffset</code> is invalid
	 */
	public byte[] getBytes(long byteOffset, int numberOfBytes) {
		byte[] bytes = new byte[numberOfBytes];
		
		if ((byteOffset + numberOfBytes) < byteLength && byteOffset >= 0) {
			for (int i = 0; i < numberOfBytes; i++) {
				bytes[i] = unsafe.getByte(address + byteOffset + i);
			}
			
			return bytes;
		} else if (byteOffset < 0) {
			System.err.println("Attempted to read from a memory address outside of the array! (" + byteOffset + " < 0)");
		} else {
			System.err.println("Attempted to read from a memory address outside of the array! (" + byteOffset + " >= " + byteLength + ")");
		}
		
		return new byte[0];
	}
	
	/**
	 * The size of the array, in bytes.
	 * 
	 * @return size of the array in bytes
	 */
	public long size() {
		return byteLength;
	}
	
	/**
	 * Frees the memory allocated for the array.
	 * <p>
	 * <b>CALL THIS WHEN YOU ARE DONE WITH THE ARRAY</b>
	 */
	public void free() {
		unsafe.freeMemory(address);
		System.out.println("Freed " + byteLength + " bytes of memory");
	}
}
