package com.example.upnpsocketapp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import org.teleal.cling.android.AndroidUpnpService;
import org.teleal.cling.android.AndroidUpnpServiceImpl;
import org.teleal.cling.binding.LocalServiceBindingException;
import org.teleal.cling.binding.annotations.AnnotationLocalServiceBinder;
import org.teleal.cling.model.DefaultServiceManager;
import org.teleal.cling.model.ValidationException;
import org.teleal.cling.model.meta.Device;
import org.teleal.cling.model.meta.DeviceDetails;
import org.teleal.cling.model.meta.DeviceIdentity;
import org.teleal.cling.model.meta.LocalDevice;
import org.teleal.cling.model.meta.LocalService;
import org.teleal.cling.model.meta.ManufacturerDetails;
import org.teleal.cling.model.meta.ModelDetails;
import org.teleal.cling.model.meta.RemoteDevice;
import org.teleal.cling.model.types.DeviceType;
import org.teleal.cling.model.types.UDADeviceType;
import org.teleal.cling.model.types.UDN;
import org.teleal.cling.registry.DefaultRegistryListener;
import org.teleal.cling.registry.RegistrationException;
import org.teleal.cling.registry.Registry;
import org.teleal.cling.registry.RegistryListener;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private ServerSocketHandler FileServer;
	private BluetoothDiscoverer bTDisc;
	private Context context;
	private ArrayList<DeviceDisplay> deviceList = new ArrayList<DeviceDisplay>();
	private String deviceName;
	private Spinner deviceSpinner;
	private Handler handler = new Handler();
	private int ipAddress;
	private ArrayAdapter<DeviceDisplay> listAdapter;
	private EditText nameText;
	private File path;
	private AndroidUpnpService upnpService;	
	private RegistryListener registryListener = new BrowseRegistryListener();
	private boolean registered = false;
	

    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            upnpService = (AndroidUpnpService) service;

            // Refresh the list with all known devices
            listAdapter.clear();
            for (Device device : upnpService.getRegistry().getDevices()) {
                ((BrowseRegistryListener) registryListener).deviceAdded(device);
            }

            // Getting ready for future device advertisements
            upnpService.getRegistry().addListener(registryListener);

            // Search asynchronously for all devices
            upnpService.getControlPoint().search();
        }

        public void onServiceDisconnected(ComponentName className) {
            upnpService = null;
        }
    };

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//bind upnp service to device
		System.out.println("calling the bindservice");
		getApplicationContext().bindService(new Intent
				(this, AndroidUpnpServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);
		System.out.println("finished the bindservice");
		
		//set up instance variables
		context = this;
		ipAddress = ((WifiManager)getSystemService("wifi")).getConnectionInfo().getIpAddress();

		//set file path
		if (isExternalStorageWritable())
			path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		else
			this.path = getFilesDir();

		//create dummy files
		createDummyFile();

		//allow the upnpserviceimpl to close
		if (Build.VERSION.SDK_INT > 9)
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

		//ui featues ---------------------------------------------------------------------------------
		//name text box
		nameText = ((EditText)findViewById(R.id.nameText));			

		//set actions for buttons
		//set action for register button
		Button registerButton = (Button)findViewById(R.id.register_button);
		registerButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View paramAnonymousView)
			{
				try
				{
					if(registered)
						System.out.println("Already Registered!");
						
					else{
						deviceName = nameText.getText().toString();
						registerLocalDevice();

						FileServer = new ServerSocketHandler(path);
						FileServer.start();

						bTDisc = new BluetoothDiscoverer(deviceName, context);
						bTDisc.start();

						notifyUser("IP address: " + getIP());
						registered = true;
					}
				}
				catch (URISyntaxException e)
				{
					System.out.println("URI constructor failed");
					e.printStackTrace();
				}
			}
		});

		//set action for refresh button
		Button refreshButton = (Button)findViewById(R.id.refresh_button);
		refreshButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View paramAnonymousView)
			{
				updateList();
			}
		});

		//set action for send file button
		Button getFileButton = (Button)findViewById(R.id.get_file_button);
		getFileButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View paramAnonymousView)
			{
				String str = ((DeviceDisplay) deviceSpinner.getSelectedItem()).getAddress();
				String[] arrayOfString = { "newTestFile.txt", "secondFile.txt" };
				new SocketClient(str, path, arrayOfString, 5000).start();
			}
		});


		//set up the spinner to its arraylist		
		deviceSpinner = ((Spinner)findViewById(R.id.device_spinner));

		listAdapter = new ArrayAdapter<DeviceDisplay>(this, android.R.layout.simple_spinner_item, deviceList);
		listAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

		deviceSpinner.setAdapter(listAdapter);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public LocalDevice createDevice(String name) throws ValidationException, IOException{
		
		System.out.println("creating a local device");
		
		DeviceIdentity identity =
	            new DeviceIdentity(
	                    UDN.uniqueSystemIdentifier("Demo Sharing Service")
	            );

	    DeviceType type =
	            new UDADeviceType("ShareDevice", 1);

	    DeviceDetails details =
	            new DeviceDetails(
	            		new URL(getIP()),
	                    deviceName,
	                    new ManufacturerDetails("UCT Hons"),
	                    new ModelDetails(
	                            "Upnp Socket",
	                            "Upnp file sharing service through sockets.",
	                            "v1"
	                    ),
	                   "" + (Math.random()*100),
	                    "upc",
	                    null
	            );

	    LocalService<ShareService> shareService =
	            new AnnotationLocalServiceBinder().read(ShareService.class);

	    shareService.setManager(
	            new DefaultServiceManager(shareService, ShareService.class)
	    );

	    return new LocalDevice(identity, type, details, shareService);
	    /* Several services can be bound to the same device:
	    return new LocalDevice(
	            identity, type, details, icon,
	            new LocalService[] {switchPowerService, myOtherService}
	    );
	    */
	}
	
	//method to update the list of remote devices in the registry
	private void updateList()
	{
		if (this.upnpService != null)
		{
			upnpService.getRegistry().removeAllRemoteDevices();
			upnpService.getControlPoint().search();
		}
	}
	
	//method to register the current device on its own registry
	private void registerLocalDevice()	throws URISyntaxException{
		try
		{
			if(upnpService == null)
				System.out.println("Upnp service hasn't been instantiated!");
			
			upnpService.getRegistry().addDevice(createDevice(deviceName));
		}
		catch (RegistrationException localRegistrationException)
		{
			System.out.println("Error adding local device: Reg error");
			localRegistrationException.printStackTrace();
			return;
		}
		catch (LocalServiceBindingException localLocalServiceBindingException)
		{
			System.out.println("Error adding local device: service binding error");
			localLocalServiceBindingException.printStackTrace();
			return;
		} catch (ValidationException e) {
			System.out.println("validation exception");
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("IOException");
			e.printStackTrace();
		}
			}
	
	//method to print output to the user on screen
	private void notifyUser(final String paramString)
	{
		//System.out.println("in notify user now" + paramString);
		handler.postDelayed(new Runnable()
		{
			public void run()
			{
				TextView localTextView = (TextView)MainActivity.this.findViewById(R.id.log_text);
				localTextView.setText(localTextView.getText() + "\n-" + paramString);
			}
		}
		, 1L);
	}
	
	//method to view if sd card is writeable
	private boolean isExternalStorageWritable()
	{
		return "mounted".equals(Environment.getExternalStorageState());
	}
	
	//method that returns a string representation of the devices ip address
	private String getIP()
	{
		return "http://" 
				+ (0xFF & this.ipAddress)
				+ "." + (0xFF & this.ipAddress >> 8)
				+ "." + (0xFF & this.ipAddress >> 16)
				+ "." + (0xFF & this.ipAddress >> 24);
		
	}
	
	//creates dummy files to be used to transfer
	private void createDummyFile()
	{
		File localFile1 = new File(this.path, "newTestFile.txt");
		File localFile2 = new File(this.path, "secondFile.txt");
		try
		{
			byte[] arrayOfByte1 = "This is a tester file created in the app dnssddemo".getBytes("UTF8");
			byte[] arrayOfByte2 = "This is a tester file created in the app dnssddemo, it is a longer file than the first file for testing purposes, just for me. I don't like have good grammar".getBytes("UTF8");
			
			localFile1.createNewFile();
			localFile2.createNewFile();
			
			if ((localFile1.exists()) && (localFile2.exists()))
			{
				FileOutputStream localFileOutputStream1 = new FileOutputStream(localFile1);
				localFileOutputStream1.write(arrayOfByte1);
				localFileOutputStream1.close();
				
				FileOutputStream localFileOutputStream2 = new FileOutputStream(localFile2);
				localFileOutputStream2.write(arrayOfByte2);
				localFileOutputStream2.close();
				
				System.out.println("2 files created: ");
			}
			return;
		}
		catch (FileNotFoundException localFileNotFoundException)
		{
			System.out.println("dummy file creation failed: " + path);
			localFileNotFoundException.printStackTrace();
			return;
		}
		catch (IOException localIOException)
		{
			System.out.println("dummy file creation failed: " + path);
			localIOException.printStackTrace();
		}
	}
	
	//method to gracefull remove all the network listeners and unbind services
	protected void onDestroy()
	{
		super.onDestroy();
		
		if (FileServer != null)
			this.FileServer.closeSocket();

		if (this.upnpService != null)
			new Thread()
		{
			public void run()
			{
				upnpService.getRegistry().removeListener(MainActivity.this.registryListener);
			}
		}.start();
		
		getApplicationContext().unbindService(this.serviceConnection);
		
		if (this.bTDisc != null)
			this.bTDisc.shutdownBluetooth();
	}
	
	//customised registry listener class, does actions on device found
	class BrowseRegistryListener extends DefaultRegistryListener
	{
		BrowseRegistryListener()
		{
		}

		public void deviceAdded(final Device paramDevice)
		{
			String url = "";
			RemoteDevice remote;
			
			if (paramDevice instanceof RemoteDevice){
				remote = (RemoteDevice) paramDevice;
				System.out.println("getting url of new device");
				url = remote.getIdentity().getDescriptorURL().getHost();
			}
			else{
				url = getIP();
			}
			final String addressURL = url;
			
			System.out.println("Added device to list");
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					
					System.out.println("Device added url: " + (addressURL));

					DeviceDisplay localDeviceDisplay = new DeviceDisplay(paramDevice, addressURL);

					int i = listAdapter.getPosition(localDeviceDisplay);

					if (i >= 0)
					{
						listAdapter.remove(localDeviceDisplay);
						listAdapter.insert(localDeviceDisplay, i);
						return;
					}

					listAdapter.add(localDeviceDisplay);
				}
			});

		}

		public void deviceRemoved(final Device paramDevice)
		{
			
			String url = "";
			RemoteDevice remote;
			
			if (paramDevice instanceof RemoteDevice){
				remote = (RemoteDevice) paramDevice;
				
				url = remote.getIdentity().getDescriptorURL().getHost();
			}
			final String addressURL = url;
			
			
			
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					listAdapter.remove(new DeviceDisplay(paramDevice, addressURL));
					listAdapter.notifyDataSetChanged();
				}
			});
		}

		public void localDeviceAdded(Registry paramRegistry, LocalDevice paramLocalDevice)
		{
			deviceAdded(paramLocalDevice);
		}

		public void localDeviceRemoved(Registry paramRegistry, LocalDevice paramLocalDevice)
		{
			deviceRemoved(paramLocalDevice);
		}

		public void remoteDeviceAdded(Registry paramRegistry, RemoteDevice paramRemoteDevice)
		{
			deviceAdded(paramRemoteDevice);
		}

		public void remoteDeviceDiscoveryFailed(Registry paramRegistry, final RemoteDevice paramRemoteDevice, final Exception paramException)
		{
			MainActivity.this.runOnUiThread(new Runnable()
			{
				public void run()
				{
					Context localContext = MainActivity.this.context;
					StringBuilder localStringBuilder = new StringBuilder("Discovery failed of '").append(paramRemoteDevice.getDisplayString()).append("': ");
					if (paramException != null);
					for (String str = paramException.toString(); ; str = "Couldn't retrieve device/service descriptors")
					{
						Toast.makeText(localContext, str, 1).show();
						return;
					}
				}
			});
			deviceRemoved(paramRemoteDevice);
		}

		public void remoteDeviceDiscoveryStarted(Registry paramRegistry, RemoteDevice paramRemoteDevice)
		{
			deviceAdded(paramRemoteDevice);
		}

		public void remoteDeviceRemoved(Registry paramRegistry, RemoteDevice paramRemoteDevice)
		{
			deviceRemoved(paramRemoteDevice);
		}
	}

	
}

