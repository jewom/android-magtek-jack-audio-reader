package com.magtek.mobile.android.mtscrademo;

import java.util.*;

public class TLVParser 
{
    public static List<HashMap<String, String>> parseEMVData(byte[] data, boolean hasSizeHeader, String stringPadRight)
    {
		List<HashMap<String, String>> fillMaps = new ArrayList<HashMap<String, String>>();
		
    	if (data != null)
    	{   		
    		int dataLen = data.length;    		    		
    		
    		if (dataLen >= 2)
    		{
    			int tlvLen = data.length;
    			byte tlvData[] = data;
    			
    			if (hasSizeHeader)
    			{
    				tlvLen = (int) ((data[0] & 0x000000FF) << 8) + (int) (data[1] & 0x000000FF) ;
	    		
    				tlvData = new byte[tlvLen];
					System.arraycopy(data, 2, tlvData, 0, tlvLen);
    			}
    			
	    		if (tlvData != null)
				{
    				int iTLV;
    				int iTag;
    				int iLen;
    				boolean bTag;
    				boolean bMoreTagBytes;
    				boolean bConstructedTag;
    				byte byteValue;
    				int lengthValue;
  				    		
    				byte tagBytes[] = null;
    				
    				final byte MoreTagBytesFlag1 	= (byte) 0x1F;
    				final byte MoreTagBytesFlag2 	= (byte) 0x80;
    				final byte ConstructedFlag 		= (byte) 0x20;
    				final byte SpecialTagFlag 		= (byte) 0xF0;
    				final byte MoreLengthFlag 		= (byte) 0x80;
    				final byte OneByteLengthMask 	= (byte) 0x7F;

    				byte TagBuffer[] = new byte[50];					

    				bTag = true;    				
    				iTLV = 0;    				
    				
    				while (iTLV < tlvData.length)
    				{
    					byteValue = tlvData[iTLV];

    					if (bTag)
    					{
    						// Get Tag
    						iTag = 0;    							
    						bMoreTagBytes = true;
    							
							while (bMoreTagBytes && (iTLV < tlvData.length))
    						{
								byteValue = tlvData[iTLV];
    							iTLV++;

    							TagBuffer[iTag] = byteValue;    					
    							
    		    				if (iTag == 0)
    		    				{
    		    					bMoreTagBytes = ((byteValue & MoreTagBytesFlag1) == MoreTagBytesFlag1);
    		    				}
    		    				else
    		    				{ 
    		    					bMoreTagBytes = ((byteValue & MoreTagBytesFlag2) == MoreTagBytesFlag2);
    		    				}
    							
    							iTag++;
    						}

							tagBytes = new byte[iTag];
							System.arraycopy(TagBuffer, 0, tagBytes, 0, iTag);
							
							bTag = false;
    					}
    					else
    					{
    						// Get Length
    	    				lengthValue = 0;
    	    				
							if ((byteValue & MoreLengthFlag) == MoreLengthFlag)
							{
	    						int nLengthBytes = (int) (byteValue & OneByteLengthMask);
	    						
    							iTLV++;
	    						iLen = 0;
	    						
								while ((iLen < nLengthBytes) && (iTLV < tlvData.length))
								{
									byteValue = tlvData[iTLV];
        							iTLV++;
						    		lengthValue = (int) ((lengthValue & 0x000000FF) << 8) + (int) (byteValue & 0x000000FF);
        							iLen++;
								}								
							}
							else
							{
								lengthValue = (int) (byteValue & OneByteLengthMask);
    							iTLV++;
							}
						
							if (tagBytes != null)
							{
								int tagByte = tagBytes[0];
								
			    				bConstructedTag = ((tagByte & ConstructedFlag) == ConstructedFlag);

			    				if (bConstructedTag) 
								{
									// Constructed
					    			HashMap<String, String> map = new HashMap<String, String>();
					    			map.put("tag", getHexString(tagBytes, 0, stringPadRight));
					    			map.put("len", "" + lengthValue);
					    			map.put("value", "[Container]");
					    			fillMaps.add(map);								
								}
								else
								{
									// Primitive									
									int endIndex = iTLV + lengthValue;
									
									if (endIndex > tlvData.length)
										endIndex =  tlvData.length;
									
									byte valueBytes[] = null;
									int len = endIndex - iTLV;
									if (len > 0)
									{
										valueBytes = new byte[len];
										System.arraycopy(tlvData, iTLV, valueBytes, 0, len);
									}
									
					    			HashMap<String, String> map = new HashMap<String, String>();
					    			map.put("tag", getHexString(tagBytes, 0, stringPadRight));
					    			map.put("len", "" + lengthValue);

					    			if (valueBytes != null)
					    				map.put("value", getHexString(valueBytes, 0, stringPadRight));
					    			else
					    				map.put("value", "");
					    			
					    			fillMaps.add(map);								

	    				    		iTLV += lengthValue;
								}
							}

							bTag = true;
    					}    					
    				}
				}
    		}
    	}

    	return fillMaps;
    }

    public static String getTagValue(List<HashMap<String, String>> fillMaps, String tagString)
    {
		String valueString = "";
	    
	    ListIterator<HashMap<String, String>> it = fillMaps.listIterator();
	    
	    while (it.hasNext())
	    {
	    	HashMap<String, String> map = it.next();
	    	
	    	if (map.get("tag").equalsIgnoreCase(tagString))
	    	{
	    		valueString = map.get("value");
	    	}
	    }
	    
	    return valueString;
    }
    
    public static String getTextString(byte[] data, int start) 
    {
    	String result = "";

    	if (data != null && data.length > 0) 
        {
    		result = getTextString(data, start, data.length);
        }
    	
    	return result;
    }
    
    public static String getTextString(byte[] data, int start, int length) 
    {    	
    	String result = "";
    	
        if (data != null && data.length > 0) 
        {
            StringBuilder stringBuilder = new StringBuilder(data.length+1);
            for(int i = start; i < length; i++)
            {
            	try
            	{
            		stringBuilder.append(String.format("%c", data[i]));
            	}
            	catch (Exception ex)
            	{
            		stringBuilder.append("<?>");            		
            	}
            }
            result = stringBuilder.toString();
        }

//        Log.i(TAG, "Data: " + result);           
		
        return result;
	}      

	public static String getHexString(byte[] data) 
    {
		return getHexString(data, 0, "");
    }
    
	public static String getHexString(byte[] data, int start, String stringPadRight) 
    {    	
    	String result = "";
    	
        if (data != null && data.length > 0) 
        {
        	int byteLength = 2;
        	
        	if (stringPadRight != null)
        	{
        		byteLength += stringPadRight.length();
        	}

        	StringBuilder stringBuilder = new StringBuilder(data.length*byteLength+1);
        	
            for(int i = start; i < data.length; i++)
            {
	        	try
	        	{
	            	stringBuilder.append(String.format("%02X", data[i]));
	        	}
	        	catch (Exception ex)
	        	{
	        		stringBuilder.append("  ");            		
	        	}

	        	if (stringPadRight != null)
	        	{
	        		stringBuilder.append(stringPadRight);
	        	}
            }
            result = stringBuilder.toString();
        }
		
        return result;
	}	

    public static byte[] getByteArrayFromHexString(String hexString) 
    {
    	return getByteArrayFromHexString(hexString, "");     
    }
    
    public static byte[] getByteArrayFromHexString(String hexString, String stringPadRight) 
    {
    	int byteLength = 2;
    	
    	if (stringPadRight != null)
    	{
    		byteLength += stringPadRight.length();
    	}
    	
        byte result[] = null;
        
        if (hexString != null)
        {        
		    result = new byte[hexString.length() / byteLength];
		    
		    char hexCharArray[] = hexString.toUpperCase().toCharArray();
		    
		    StringBuffer sbCurrent;
		    
		    for (int i = 0; i < result.length; i++) 
		    {
		    	sbCurrent = new StringBuffer("");
		    	sbCurrent.append(String.valueOf(hexCharArray[i*byteLength]));
		    	sbCurrent.append(String.valueOf(hexCharArray[i*byteLength + 1]));
		    	try
		    	{
		    		result[i] = (byte) Integer.parseInt(sbCurrent.toString(), 16);
		    	}
		    	catch (Exception ex)
		    	{
		    		
		    	}
		    }
        }
        
        return result;
    }    
		
}
