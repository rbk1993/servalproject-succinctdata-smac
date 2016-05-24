package org.servalproject.succinctdata;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Random;
import java.util.Scanner;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.magdaaproject.sam.InReachMessageHandler;
import org.magdaaproject.sam.RCLauncherActivity;
import org.servalproject.sam.R;

import com.delorme.inreachcore.InReachManager;
import com.delorme.inreachcore.OutboundMessage;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.v4.content.LocalBroadcastManager;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

class UploadFormSpecificationTask extends AsyncTask<String, String, Long> {
	public static Handler handler = null;
	public static Context context = null;

	protected Long doInBackground(String... forms) {
		for (int i = 0; i < forms.length; i++) {
			String xmlForm = forms[i];
			String resultMessage = "Unknown error while uploading Magpi form to Succinct Data server";

			{
				// Upload form specification to Succinct Data server
				String url = "http://serval1.csem.flinders.edu.au/succinctdata/upload-form.php";

				HttpClient httpclient = new DefaultHttpClient();

				HttpPost httppost = new HttpPost(url);

				InputStream stream = new ByteArrayInputStream(
						xmlForm.getBytes());
				InputStreamEntity reqEntity = new InputStreamEntity(stream, -1);
				reqEntity.setContentType("text/xml");
				reqEntity.setChunked(true); // Send in multiple parts if needed
				httppost.setEntity(reqEntity);
				int httpStatus = -1;
				try {
					HttpResponse response = httpclient.execute(httppost);
					httpStatus = response.getStatusLine().getStatusCode();
					if (httpStatus != 200) {
						resultMessage = "Failed to upload Magpi form to Succinct Data server: http result = "
								+ httpStatus;
					} else {
						resultMessage = "Successfully uploaded Magpi form to Succinct Data server: http result = "
								+ httpStatus;
						// Remember uploaded form so that we don't try to send
						// it again after we have 
						// done so once.
						SuccinctDataQueueDbAdapter db = new SuccinctDataQueueDbAdapter(
								UploadFormSpecificationTask.context);
						db.open();
						db.rememberThing(xmlForm);
						db.close();
					}
				} catch (Exception e) {
					// resultMessage = "Failed to upload Magpi form to "
					// + "Succinct Data server due to exception: "
					// 		+ e.toString();
					// Just return, don't display an error when trying to upload the form
					return -1L;
				}
				// Do something with response...
				Log.d("succinctdata", resultMessage);
				this.publishProgress(resultMessage);
			}
		}
		Long status = -1L;
		return status;
	}

	@Override
	protected void onProgressUpdate(String... values) {
		Toast.makeText(context, values[0], Toast.LENGTH_LONG).show();
	}

}

public class SuccinctDataQueueService extends Service {

	private String SENT = "SMS_SENT";

	public static int sms_tx_result = -1;
	public static PendingIntent sms_tx_pintent = null;

	private static Long pendingInReachMessageId = -1L;
	private static String pendingInReachMessagePiece = null;

	private boolean inReachReadyAndAvailable = false;

	private SuccinctDataQueueDbAdapter db = null;
	Thread messageSenderThread = null;

	public static SuccinctDataQueueService instance = null;

	private Handler handler = null;

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {

		instance = this;

		// Create background thread that continuously checks for messages, and
		// sends them if it can
		final Service theService = this;
		if (messageSenderThread == null) {
			messageSenderThread = new Thread(new Runnable() {
				public void run() {
					try {
						messageSenderLoop(theService);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
			messageSenderThread.start();
		}

		// Create pending intent and broadcast listener for SMS dispatch if not
		// done
		// already
		if (sms_tx_pintent == null) {

			try {
				sms_tx_pintent = PendingIntent.getBroadcast(this, 0,
						new Intent(SENT), 0);
			} catch (Exception e) {
				// just don't force quit
			}

			// ---when the SMS has been sent---
			registerReceiver(new BroadcastReceiver() {
				@Override
				public void onReceive(Context arg0, Intent arg1) {
					sms_tx_result = getResultCode();
				}
			}, new IntentFilter(SENT));
		}

		// Check if the passed intent has a message to queue
		try {
			String succinctData[] = intent
					.getStringArrayExtra("org.servalproject.succinctdata.SUCCINCT");
			String xmlData = intent
					.getStringExtra("org.servalproject.succinctdata.XML");
			String xmlForm = intent
					.getStringExtra("org.servalproject.succinctdata.XMLFORM");
			String formname = intent
					.getStringExtra("org.servalproject.succinctdata.FORMNAME");
			String formversion = intent
					.getStringExtra("org.servalproject.succinctdata.FORMVERSION");
			
			if (db == null) {
				db = new SuccinctDataQueueDbAdapter(this);
				db.open();
			}

			if (xmlData != null && db.isThingNew(xmlData) == true) {
				// Form is new, so process it.

				if (xmlData != null) RCLauncherActivity.sawUniqueMagpiRecord();			
				
				if (xmlForm != null && db.isThingNew(xmlForm)) {
					UploadFormSpecificationTask.handler = handler;
					UploadFormSpecificationTask.context = getBaseContext();
					new UploadFormSpecificationTask().execute(xmlForm);
				}

				// For each piece, create a message in the queue
				Log.d("SuccinctData", "Opening queue database");
				Log.d("SuccinctData", "Opened queue database");
				if (succinctData != null) {
					// Send ALL pieces before marking as having been remembered
					for (int i = 0; i < succinctData.length; i++) {
						String piece = succinctData[i];
						String prefix = piece.substring(0, 10);					
						db.createQueuedMessage(prefix, piece, formname + "/"
								+ formversion, xmlData);
					}
					// Mark this record as having been queued so that we don't queue it again
					db.rememberThing(xmlData);	  
					
					Intent i = new Intent("SD_MESSAGE_QUEUE_UPDATED");
					LocalBroadcastManager lb = LocalBroadcastManager
							.getInstance(this);
					lb.sendBroadcastSync(i);
				}
			}
		} catch (Exception e) {
			String s = e.toString();
			Log.e("SuccinctDataqQueueService", "Exception: " + s);
		}

		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		instance = this;
		return null;
	}

	// Detecting cellular service (SMS, not data)
	// This method by santacrab from:
	// http://stackoverflow.com/questions/6435861/android-what-is-the-correct-way-of-checking-for-mobile-network-available-no-da
	public static Boolean isSMSAvailable(Context appcontext) {
		try {
			TelephonyManager tel = (TelephonyManager) appcontext
					.getSystemService(Context.TELEPHONY_SERVICE);
			return ((tel.getNetworkOperator() != null && tel
					.getNetworkOperator().equals("")) ? false : true);
		} catch (Exception e) {
			return false;
		}
	}

	// Detecting internet access by Alexandre Jasmin from:
	// http://stackoverflow.com/questions/4238921/detect-whether-there-is-an-internet-connection-available-on-android
	public boolean isInternetAvailable() {
		ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo activeNetworkInfo = connectivityManager
				.getActiveNetworkInfo();
		return activeNetworkInfo != null && activeNetworkInfo.isConnected();
	}

	private int sendBadRecordViaInternet(String form, String record) {
		// XXX make configurable!
		String url = "http://serval1.csem.flinders.edu.au/succinctdata/badrecordupload.php";

		HttpClient httpclient = new DefaultHttpClient();

		HttpPost httppost = new HttpPost(url);

		InputStream stream = new ByteArrayInputStream((form+"\n--------------------\n"+record).getBytes());
		InputStreamEntity reqEntity = new InputStreamEntity(stream,-1);
		reqEntity.setContentType("text/xml");
	    reqEntity.setChunked(true); // Send in multiple parts if needed

		httppost.setEntity(reqEntity);
		
		int httpStatus = -1;
		try {
			HttpResponse response = httpclient.execute(httppost);
			httpStatus = response.getStatusLine().getStatusCode();
		} catch (Exception e) {
			return -1;
		}
		// Do something with response...
		if (httpStatus != 200)
			return -1;
		else
			return 0;
	}

	
	private int sendViaCellular(String succinctData) {
		// XXX make configurable!
		String url = "http://serval1.csem.flinders.edu.au/succinctdata/upload.php";

		HttpClient httpclient = new DefaultHttpClient();

		HttpPost httppost = new HttpPost(url);

		InputStream stream = new ByteArrayInputStream(succinctData.getBytes());
		InputStreamEntity reqEntity = new InputStreamEntity(stream, -1);
		reqEntity.setContentType("text/succinctdata");
		reqEntity.setChunked(true); // Send in multiple parts if needed
		httppost.setEntity(reqEntity);
		int httpStatus = -1;
		try {
			HttpResponse response = httpclient.execute(httppost);
			httpStatus = response.getStatusLine().getStatusCode();
		} catch (Exception e) {
			return -1;
		}
		// Do something with response...
		if (httpStatus != 200)
			return -1;
		else
			return 0;
	}

	public int sendSMS(String smsnumber, String message) {
		SuccinctDataQueueService.sms_tx_result = 0xbeef;

		try {
			Intent sentIntent = new Intent(SENT);
			/* Create Pending Intents */
			PendingIntent p = PendingIntent.getBroadcast(
					getApplicationContext(), 0, sentIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);

			// Dispatch SMS
			SmsManager manager = SmsManager.getDefault();

			try {
				manager.sendTextMessage(smsnumber, null, message, p, null);
			} catch (Exception e) {
				return -1;
			}
		} catch (Exception e) {
			return -1;
		}

		// Then wait for pending intent to indicate delivery.
		// We catch the intent in this class, and then poll the result flag to
		// see what happened.
		// Give 60 seconds for the SMS to get sent
		for (int i = 0; i < 60; i++) {
			if (SuccinctDataQueueService.sms_tx_result == 0xbeef)
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			else if (SuccinctDataQueueService.sms_tx_result == Activity.RESULT_OK)
				return 0;
			else if (SuccinctDataQueueService.sms_tx_result == 1) {
				// SMS transmission failed -- perhaps phone has a network, but
				// no
				// credit to send.
				return -1;
			}
		}

		return -1;
	}

	private int sendInReach(String phonenumber, String[] succinctData,
			PendingIntent p) {
		try {
			int ms_messageIdentifier = 0;
			InReachManager manager = InReachMessageHandler.getInstance()
					.getService().getManager();
			for (int i = 0; i < succinctData.length; i++) {
				final OutboundMessage message = new OutboundMessage();
				message.setAddressCode(OutboundMessage.AC_FreeForm);
				message.setMessageCode(OutboundMessage.MC_FreeTextMessage);
				// Set message identifier to first few bytes of hash of data
				try {
					MessageDigest md;
					md = MessageDigest.getInstance("SHA-1");
					md.update(succinctData[0].getBytes("iso-8859-1"), 0,
							succinctData[0].length());
					byte[] sha1hash = md.digest();
					ms_messageIdentifier = sha1hash[0] + (sha1hash[1] << 8)
							+ (sha1hash[2] << 16) + (sha1hash[3] << 24);
				} catch (Exception e) {
					Random r = new Random();
					int i1 = r.nextInt(1000000000);
					ms_messageIdentifier = i1;
				}
				message.setIdentifier(ms_messageIdentifier);
				message.addAddress(phonenumber);
				message.setText(succinctData[i]);

				// queue the message for sending
				if (!manager.sendMessage(message)) {
					// Failed
					return -1;
				}

			}
			return ms_messageIdentifier;
		} catch (Exception e) {
			// Something bad happened
			return -1;
		}
	}

	public void messageSenderLoop(Service s) {
		// XXX - This really is ugly. We should edge detect everything instead
		// of
		// polling.
		instance = this;

		Looper.prepare();
		SuccinctDataQueueDbAdapter db = new SuccinctDataQueueDbAdapter(this);
		db.open();

		String smsnumber = getString(R.string.succinct_data_sms_number);

		long next_timeout = 5000;

		// On startup, look for fail-safe file to upload in case we force-quit
		if (isInternetAvailable()) {
			String failSafeFileName =
					Environment.getExternalStorageDirectory().getPath()+
                    getBaseContext().getString(R.string.system_file_path_succinct_specification_files_path)+"/failsafe-form.txt";
			File failSafeFormFile = new File(failSafeFileName);
			failSafeFileName =
                    Environment.getExternalStorageDirectory().getPath()+
                    getBaseContext().getString(R.string.system_file_path_succinct_specification_files_path)+"/failsafe-record.txt";
			File failSafeRecordFile = new File(failSafeFileName);
			if (failSafeFormFile.exists()&&failSafeRecordFile.exists()) {
				try {
				// Upload record, and delete if successful
				FileInputStream fileInputStream = new FileInputStream(failSafeFormFile);
				int bytesAvailable = fileInputStream.available();
				byte[] buffer = new byte[bytesAvailable];
				int bytesRead = fileInputStream.read(buffer,0,bytesAvailable);				
				String form = buffer.toString();
				fileInputStream.close();
				
				fileInputStream = new FileInputStream(failSafeRecordFile);
				bytesAvailable = fileInputStream.available();
				buffer = new byte[bytesAvailable];
				bytesRead = fileInputStream.read(buffer,0,bytesAvailable);				
				String record= buffer.toString();
				fileInputStream.close();
				
				if (sendBadRecordViaInternet(form,record) == 0) {
					failSafeFormFile.delete();
					failSafeRecordFile.delete();
				}
				} catch(Exception e) {
				
				}
				}
			}

		
		while (true) {
			// Wait a little while before trying again
			try {
				Thread.sleep(next_timeout);
			} catch (Exception e) {
			}			
			
			if (isInternetAvailable()) {
				// See if we have bad records to upload
				Cursor c = db.fetchAllBadRecords();
				if (c.getCount() > 0) {
					c.moveToFirst();
					while (c.isAfterLast() == false) {
						String form = c.getString(1);
						String record = c.getString(2);
						
						// Upload record, and delete if successful
						
						if (sendBadRecordViaInternet(form,record) == 0) {
							db.deleteBadRecord(form,record);
						}
						
						c.moveToNext();
					}
				}
				
			}
			
			// Get number of messages in database
			Cursor c = db.fetchAllMessages();
			RCLauncherActivity.set_message_queue_length(c.getCount());
			if (c.getCount() == 0) {
				// If no queued messages, wait only a few seconds
				next_timeout = 5000;
				continue;
			}

			c.moveToFirst();
			while (c.isAfterLast() == false) {
				String prefix = c.getString(1);
				String piece = c.getString(4);
				String xml = c.getString(5);

				// Update inReach status
				if (InReachMessageHandler.isInreachAvailable() == true) {
					// inReach is available.
					// but is there a queued message?
					inReachReadyAndAvailable = true;
				}

				// If data service is available, try to send messages that way
				boolean messageSent = false;
				if ((messageSent == false) && isInternetAvailable()) {
					if (sendViaCellular(piece) == 0)
						messageSent = true;
				}
				// Else, if SMS is available, try to send messages that way
				if ((messageSent == false) && isSMSAvailable(s)) {
					if (sendSMS(smsnumber, piece) == 0)
						messageSent = true;
				}
				if ((messageSent == false)
						&& (inReachReadyAndAvailable == true)) {
					// Else, if inReach is available, try to send messages that
					// way
					if (dispatchViaInReach(smsnumber, piece) == 0) {
						// Mark inreach busy until it confirms handover of the
						// message
						// to the satellite constellation.
						inReachReadyAndAvailable = false;
					}
				}

				if (messageSent == true) {
					// Delete message from database
					db.delete(piece);
					Intent i = new Intent("SD_MESSAGE_QUEUE_UPDATED");
					LocalBroadcastManager lb = LocalBroadcastManager
							.getInstance(s);
					lb.sendBroadcastSync(i);
				}

				c.moveToNext();
			}

			// Check if we still have messages queued. If so, there is some
			// problem
			// with sending them, so hold off for a couple of minutes before
			// trying again.
			c = db.fetchAllMessages();
			if (c.getCount() == 0) {
				// If no queued messages, wait only a few seconds
				next_timeout = 5000;
			} else {
				// Was 2 minutes
				next_timeout = 5000;
			}

		}
	}

	private int dispatchViaInReach(String smsnumber, String piece) {
		// XXX - Need synchronous inReach sending code here

		// XXX - tie piece to inReach message ID so that when the inReach
		// indicates that it has been delivered, we can remove the relevant
		// piece from the database, even if it has taken hours for the inReach
		// to deliver it.

		Intent sentIntent = new Intent("SUCCINCT_DATA_INREACH_SEND_STATUS");
		/* Create Pending Intents */
		PendingIntent sentPI = PendingIntent.getBroadcast(
				getApplicationContext(), 0, sentIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		long inReachMessageId = (long) sendInReach(smsnumber,
				new String[] { piece }, sentPI);

		rememberPendingInReachMessage(inReachMessageId, piece);

		// XXX - For some reason the inReach messages are not being properly
		// acknowledged via
		// the messageid, or at least that it is what it seems like, since the
		// messages in the
		// queue get sent via the inreach again and again and again ...
		// So for now, we will just delete the queued message from the db as
		// soon as we have
		// passed it to the inreach. This makes it possible to lose messages
		// under certain
		// circumstances. This is why it is important for now to tell people to
		// flush the
		// magpi records via the internet as well. We will work to solve this
		// problem more
		// permanently in due course.
		// Delete message from database
		{
			db.delete(piece);
			Intent i = new Intent("SD_MESSAGE_QUEUE_UPDATED");
			LocalBroadcastManager lb = LocalBroadcastManager
					.getInstance(getApplicationContext());
			lb.sendBroadcastSync(i);
		}

		return 0;
	}

	private void rememberPendingInReachMessage(Long inReachMessageId,
			String piece) {
		pendingInReachMessageId = inReachMessageId;
		pendingInReachMessagePiece = piece;
	}

	public static void sawInReachMessageConfirmation(Long inReachMessageId) {
		if (inReachMessageId == pendingInReachMessageId) {
			// We know about this one, delete the corresponding piece from the
			// database.
			SuccinctDataQueueService.instance.db
					.delete(pendingInReachMessagePiece);
			Intent i = new Intent("SD_MESSAGE_QUEUE_UPDATED");
			LocalBroadcastManager lb = LocalBroadcastManager
					.getInstance(SuccinctDataQueueService.instance);
			lb.sendBroadcastSync(i);
		} else {
			Log.d("inreachtx", "This message id (" + inReachMessageId
					+ "}) does not match the one we are waiting for ("
					+ pendingInReachMessageId + ").");
		}
	}
}
