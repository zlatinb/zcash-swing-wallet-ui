/************************************************************************************************
 *  _________          _     ____          _           __        __    _ _      _   _   _ ___
 * |__  / ___|__ _ ___| |__ / ___|_      _(_)_ __   __ \ \      / /_ _| | | ___| |_| | | |_ _|
 *   / / |   / _` / __| '_ \\___ \ \ /\ / / | '_ \ / _` \ \ /\ / / _` | | |/ _ \ __| | | || |
 *  / /| |__| (_| \__ \ | | |___) \ V  V /| | | | | (_| |\ V  V / (_| | | |  __/ |_| |_| || |
 * /____\____\__,_|___/_| |_|____/ \_/\_/ |_|_| |_|\__, | \_/\_/ \__,_|_|_|\___|\__|\___/|___|
 *                                                 |___/
 *
 * Copyright (c) 2016 Ivan Vaklinov <ivan@vaklinov.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 **********************************************************************************/
package com.vaklinov.zcashui;


import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonArray;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.eclipsesource.json.ParseException;


/**
 * Calls zcash-cli
 *
 * @author Ivan Vaklinov <ivan@vaklinov.com>
 */
public class ZCashClientCaller
{
	public static class WalletBalance
	{
		public double transparentBalance;
		public double privateBalance;
		public double totalBalance;
	}

	public static class WalletCallException
		extends Exception
	{
		public WalletCallException(String message)
		{
			super(message);
		}

		public WalletCallException(String message, Throwable cause)
		{
			super(message, cause);
		}
	}

	private File zcashcli;

	public ZCashClientCaller(String installDir)
		throws IOException
	{
		// Detect daemon and client tools installation
		File dir = new File(installDir);
	    zcashcli = new File(dir, "zcash-cli");

		if (!zcashcli.exists())
		{
			throw new IOException(
				"The ZCash installation directory " + installDir + " needs to contain " +
				"the command line utilities zcashd and zcash-cli. zcash-cli is missing!");
		}
	}


	public synchronized WalletBalance getWalletInfo()
		throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
	    	this.zcashcli.getCanonicalPath(), "z_gettotalbalance"
	    });

	    String strResponse = caller.execute();
	    if (strResponse.trim().startsWith("error:"))
	    {
	    	throw new WalletCallException("Error response from wallet: " + strResponse);
	    }

	    JsonValue response = null;
	    try
	    {
	    	response = Json.parse(strResponse);
	    } catch (ParseException pe)
	    {
	    	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
	    }

		WalletBalance balance = new WalletBalance();

		if (response.isObject())
		{
			JsonObject objResponse = response.asObject();
		    if (objResponse.get("error") != null)
		    {
		    	throw new WalletCallException("Error response from wallet: " + response.toString());
		    } else
		    {
		    	balance.transparentBalance = Double.valueOf(objResponse.getString("transparent", "-1"));
		    	balance.privateBalance = Double.valueOf(objResponse.getString("private", "-1"));
		    	balance.totalBalance = Double.valueOf(objResponse.getString("total", "-1"));
		    }
		} else
		{
			throw new WalletCallException("Unexpected non-object response from wallet: " + response.toString());
		}

		return balance;
	}


	public synchronized String[][] getWalletPublicTransactions()
		throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
	    	this.zcashcli.getCanonicalPath(), "listtransactions"
	    });

	    String strResponse = caller.execute();
	    if (strResponse.trim().startsWith("error:"))
	    {
	    	throw new WalletCallException("Error response from wallet: " + strResponse);
	    }

	    JsonValue response = null;
	    try
	    {
	    	response = Json.parse(strResponse);
	    } catch (ParseException pe)
	    {
	    	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
	    }

	    if (!response.isArray())
	    {
	    	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
	    }

	    JsonArray jsonTransactions = response.asArray();
	    String strTransactions[][] = new String[jsonTransactions.size()][];
	    for (int i = 0; i < jsonTransactions.size(); i++)
	    {
	    	strTransactions[i] = new String[5];
	    	JsonObject trans = jsonTransactions.get(i).asObject();

	    	strTransactions[i][0] = "T (Public)";
	    	strTransactions[i][1] = trans.getString("category", "ERROR!");
	    	strTransactions[i][2] = trans.get("amount").toString();
	    	strTransactions[i][3] = trans.get("time").toString();
	    	strTransactions[i][4] = trans.getString("address", "ERROR!");
	    }

	    return strTransactions;
	}


	public synchronized String[] getWalletZAddresses()
		throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
		    this.zcashcli.getCanonicalPath(), "z_listaddresses"
		});

		String strResponse = caller.execute();
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		JsonValue response = null;
		try
		{
		  	response = Json.parse(strResponse);
		} catch (ParseException pe)
		{
		  	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
		}

		if (!response.isArray())
		{
		   	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
		}

		JsonArray jsonAddresses = response.asArray();
		String strAddresses[] = new String[jsonAddresses.size()];
		for (int i = 0; i < jsonAddresses.size(); i++)
		{
		    strAddresses[i] = jsonAddresses.get(i).asString();
		}

	    return strAddresses;
	}


	public synchronized String[][] getWalletZReceivedTransactions()
		throws WalletCallException, IOException, InterruptedException
	{
		String[] zAddresses = this.getWalletZAddresses();

		List<String[]> zReceivedTransactions = new ArrayList<String[]>();

		for (String zAddress : zAddresses)
		{
		    CommandExecutor caller = new CommandExecutor(new String[]
		    {
			   	this.zcashcli.getCanonicalPath(), "z_listreceivedbyaddress", zAddress
			});

		    String strResponse = caller.execute();
		    if (strResponse.trim().startsWith("error:"))
		    {
		    	throw new WalletCallException("Error response from wallet: " + strResponse);
		    }

		    JsonValue response = null;
		    try
		    {
		    	response = Json.parse(strResponse);
		    } catch (ParseException pe)
		    {
		    	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
		    }

		    if (!response.isArray())
		    {
		    	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
		    }

		    JsonArray jsonTransactions = response.asArray();
		    for (int i = 0; i < jsonTransactions.size(); i++)
		    {
		    	String[] currentTransaction = new String[5];
		    	JsonObject trans = jsonTransactions.get(i).asObject();

		    	currentTransaction[0] = "Z (Private)";
		    	currentTransaction[1] = "receive";
		    	currentTransaction[2] = trans.get("amount").toString();
		    	String txID = trans.getString("txid", "ERROR!");
		    	currentTransaction[3] = this.getWalletTransactionTime(txID);
		    	currentTransaction[4] = zAddress;

		    	zReceivedTransactions.add(currentTransaction);
		    }
		}

		return zReceivedTransactions.toArray(new String[0][]);
	}

	
	// ./src/zcash-cli listunspent only returns T addresses it seems
	public synchronized String[] getWalletPublicAddressesWithUnspentOutputs()
		throws WalletCallException, IOException, InterruptedException
	{
		CommandExecutor caller = new CommandExecutor(new String[]
	    {
	    	this.zcashcli.getCanonicalPath(), "listunspent"
	    });

	    String strResponse = caller.execute();
	    if (strResponse.trim().startsWith("error:"))
	    {
	    	throw new WalletCallException("Error response from wallet: " + strResponse);
	    }

	    JsonValue response = null;
	    try
	    {
	    	response = Json.parse(strResponse);
	    } catch (ParseException pe)
	    {
	    	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
	    }

	    if (!response.isArray())
	    {
		   	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
		}

		JsonArray jsonUnspentOutputs = response.asArray();
		Set<String> addresses = new HashSet<>();
	    for (int i = 0; i < jsonUnspentOutputs.size(); i++)
	    {
	    	JsonObject outp = jsonUnspentOutputs.get(i).asObject();
	    	addresses.add(outp.getString("address", "ERROR!"));
	    }

	    return addresses.toArray(new String[0]);
     }

	
	
	// return UNIX time as tring
	public synchronized String getWalletTransactionTime(String txID)
		throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
		    this.zcashcli.getCanonicalPath(), "gettransaction", txID
		});

		String strResponse = caller.execute();
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		JsonValue response = null;
		try
		{
		  	response = Json.parse(strResponse);
		} catch (ParseException pe)
		{
		  	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
		}

		if (!response.isObject())
		{
		   	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
		}

		JsonObject jsonTransaction = response.asObject();
		return String.valueOf(jsonTransaction.getLong("time", -1));
	}
	
	
	public synchronized String getBalanceForAddress(String address)
		throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
		    this.zcashcli.getCanonicalPath(), "z_getbalance", address
		});

	    String strResponse = caller.execute();
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		JsonValue response = null;
		try
		{
		  	response = Json.parse(strResponse);
		} catch (ParseException pe)
		{
		  	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
		}
		
		return String.valueOf(response.toString());
	}
	

	public synchronized String createNewAddress(boolean isZAddress)
		throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
		    this.zcashcli.getCanonicalPath(), (isZAddress ? "z_" : "") + "getnewaddress"
		});

	    String strResponse = caller.execute();
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		return strResponse.trim();
	}
	

	// Returns OPID
	public synchronized String sendCash(String from, String to, String amount, String memo)
		throws WalletCallException, IOException, InterruptedException
	{
		StringBuilder hexMemo = new StringBuilder();
		for (byte c : memo.getBytes("UTF-8"))
		{
			String hexChar = Integer.toHexString((int)c);
			if (hexChar.length() < 2)
			{
				hexChar = "0" + hashCode();
			}
			hexMemo.append(hexChar);
		}
		
		JsonObject toArgument = new JsonObject();
		toArgument.set("address", to);
		if (hexMemo.length() >= 2)
		{
			toArgument.set("memo", hexMemo.toString());
		}
		
		// The JSON Builder has a problem with double values that have no fractional part
		// TODO: find a better way to format the amount
		toArgument.set("amount", "\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF");
		
		JsonArray toMany = new JsonArray();
		toMany.add(toArgument);
		
		String[] sendCashParameters = new String[]
	    {
		    this.zcashcli.getCanonicalPath(), "z_sendmany", from,
		    // This replacement is a hack to make the JSON object have real format 0.00 etc.
		    // TODO: find a better way to format the amount
		    toMany.toString().replace("\"amount\":\"\uFFFF\uFFFF\uFFFF\uFFFF\uFFFF\"", 
		    		                  "\"amount\":" + new DecimalFormat("########0.00######").format(Double.valueOf(amount)))
		};
		System.out.println("Sending cash with the following command: " + 
		                   sendCashParameters[0] + " " + sendCashParameters[1] + " " + 
		                   sendCashParameters[2] + " " + sendCashParameters[3] + ".");
		
	    CommandExecutor caller = new CommandExecutor(sendCashParameters);
	    String strResponse = caller.execute();
	    
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		return strResponse.trim();
	}
	
	
	public boolean isSendingOperationComplete(String opID)
	    throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
		    this.zcashcli.getCanonicalPath(), "z_getoperationstatus", "[\"" + opID + "\"]"
		});

		String strResponse = caller.execute();
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		JsonValue response = null;
		try
		{
		  	response = Json.parse(strResponse);
		} catch (ParseException pe)
		{
		  	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
		}

		if (!response.isArray())
		{
		   	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
		}

		JsonObject jsonStatus = response.asArray().get(0).asObject();
		String status = jsonStatus.getString("status", "ERROR!");

		if (status.equalsIgnoreCase("success") || 
			status.equalsIgnoreCase("error") || 
			status.equalsIgnoreCase("failed"))
		{
			return true;
		} else if (status.equalsIgnoreCase("executing") || status.equalsIgnoreCase("queued"))
		{
			return false;
		} else
		{
			throw new WalletCallException("Unexpected status response from wallet: " + strResponse);
		}
	}
	
	
	public boolean isCompletedOperationSuccessful(String opID)
	    throws WalletCallException, IOException, InterruptedException
	{
		// TODO: unify methods - code duplication exists
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
		    this.zcashcli.getCanonicalPath(), "z_getoperationstatus", "[\"" + opID + "\"]"
		});

		String strResponse = caller.execute();
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		JsonValue response = null;
		try
		{
		  	response = Json.parse(strResponse);
		} catch (ParseException pe)
		{
		  	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
		}

		if (!response.isArray())
		{
		   	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
		}

		JsonObject jsonStatus = response.asArray().get(0).asObject();
		String status = jsonStatus.getString("status", "ERROR!");

		if (status.equalsIgnoreCase("success"))
		{
			return true;
		} else if (status.equalsIgnoreCase("error") || status.equalsIgnoreCase("failed"))
		{
			return false;
		} else
		{
			throw new WalletCallException("Unexpected final operation status response from wallet: " + strResponse);
		}
	}


	// May only be caled for already failed operatoins
	public String getOperationFinalErrorMessage(String opID)
	    throws WalletCallException, IOException, InterruptedException
	{
	    CommandExecutor caller = new CommandExecutor(new String[]
	    {
		    this.zcashcli.getCanonicalPath(), "z_getoperationstatus", "[\"" + opID + "\"]"
		});

		String strResponse = caller.execute();
		if (strResponse.trim().startsWith("error:"))
		{
		  	throw new WalletCallException("Error response from wallet: " + strResponse);
		}

		JsonValue response = null;
		try
		{
		  	response = Json.parse(strResponse);
		} catch (ParseException pe)
		{
		  	throw new WalletCallException(strResponse + "\n" + pe.getMessage() + "\n", pe);
		}

		if (!response.isArray())
		{
		   	throw new WalletCallException("Unexpected response from wallet: " + strResponse);
		}

		JsonObject jsonStatus = response.asArray().get(0).asObject();
		JsonObject jsonError = jsonStatus.get("error").asObject();
		return jsonError.getString("message", "ERROR!");
	}
}