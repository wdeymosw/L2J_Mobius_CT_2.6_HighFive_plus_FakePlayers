/*
 * Copyright (c) 2013 L2jMobius
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.l2jmobius.loginserver.crypt;

import java.io.IOException;

import org.l2jmobius.commons.network.Buffer;

/**
 * Class to use a blowfish cipher with ECB processing.<br>
 * Static methods are present to append/check the checksum of<br>
 * packets exchanged between the following partners:<br>
 * Login Server <-> Game Client<br>
 * Login Server <-> Game Server<br>
 * Also a static method is provided for the initial xor encryption between Login Server <-> Game Client.
 */
public class NewCrypt
{
	private final BlowfishEngine _crypter;
	private final BlowfishEngine _decrypter;
	
	public NewCrypt(byte[] blowfishKey)
	{
		_crypter = new BlowfishEngine();
		_crypter.init(true, blowfishKey);
		_decrypter = new BlowfishEngine();
		_decrypter.init(false, blowfishKey);
	}
	
	public NewCrypt(String key)
	{
		this(key.getBytes());
	}
	
	public static boolean verifyChecksum(Buffer data, final int offset, final int size)
	{
		// check if size is multiple of 4 and if there is more then only the checksum
		if (((size & 3) != 0) || (size <= 4))
		{
			return false;
		}
		
		long checksum = 0;
		final int count = size - 4;
		int i;
		for (i = offset; i < count; i += 4)
		{
			checksum ^= data.readInt(i);
		}
		
		return data.readInt(i) == checksum;
	}
	
	public static void appendChecksum(Buffer data, final int offset, final int size)
	{
		int checksum = 0;
		final int count = size - 4;
		int i;
		for (i = offset; i < count; i += 4)
		{
			checksum ^= data.readInt(i);
		}
		
		data.writeInt(i, checksum);
	}
	
	/**
	 * Packet is first XOR encoded with <code>key</code> Then, the last 4 bytes are overwritten with the the XOR "key". Thus this assume that there is enough room for the key to fit without overwriting data.
	 * @param raw The raw bytes to be encrypted
	 * @param offset The begining of the data to be encrypted
	 * @param size Length of the data to be encrypted
	 * @param key The 4 bytes (int) XOR key
	 */
	public static void encXORPass(Buffer raw, final int offset, final int size, int key)
	{
		final int stop = size - 8;
		int pos = 4 + offset;
		int edx;
		int ecx = key; // Initial xor key
		while (pos < stop)
		{
			edx = raw.readInt(pos);
			ecx += edx;
			edx ^= ecx;
			raw.writeInt(pos, edx);
			pos += 4;
		}
		
		raw.writeInt(pos, ecx);
	}
	
	public synchronized void decrypt(Buffer raw, final int offset, final int size) throws IOException
	{
		final int block = _decrypter.getBlockSize();
		final int count = size / block;
		for (int i = 0; i < count; i++)
		{
			_decrypter.processBlock(raw, offset + (i * block));
		}
	}
	
	public void crypt(Buffer raw, final int offset, final int size) throws IOException
	{
		int block = _crypter.getBlockSize();
		int count = size / block;
		for (int i = 0; i < count; i++)
		{
			_crypter.processBlock(raw, offset + (i * block));
		}
	}
}
