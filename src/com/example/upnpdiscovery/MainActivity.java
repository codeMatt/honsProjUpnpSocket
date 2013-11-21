package com.example.upnpdiscovery;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;

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
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private Context context;
	private String deviceName, logText;
	private Handler handler = new Handler();
	private int ipAddress;
	private File path;
	private EditText nameText;
	private AndroidUpnpService upnpService;
	private BluetoothDiscoverer bluetooth;
	private ArrayList<DeviceDisplay> deviceList;
	private RegistryListener registryListener = new BrowseRegistryListener();
	private boolean registered = false;
	
	//service connection binds the service of the application to the upnpService object
	//when called in oncreate(), this links the service listener to the registry
    private ServiceConnection serviceConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className, IBinder service) {
            upnpService = (AndroidUpnpService) service;

            // Refresh the list with all known devices
            //listAdapter.clear();
            for (Device device : upnpService.getRegistry().getDevices()) {
                ((BrowseRegistryListener) registryListener).deviceAdded(device);
            }

            // Getting ready for future device advertisements
            upnpService.getRegistry().addListener(registryListener);
            addToLog("UPnP service running: " + new Timestamp(new Date().getTime()));
            // Search asynchronously for all devices
            upnpService.getControlPoint().search();
            notifyUser("UPnP Library running");
            //registerLocalDevice();
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
		addToLog("Starting cling service: " + new Timestamp(new Date().getTime()));
		getApplicationContext().bindService(new Intent
				(this, AndroidUpnpServiceImpl.class), serviceConnection, Context.BIND_AUTO_CREATE);
		
		addToLog("UPnP Service active on device");
		
		//set up instance variables
		context = this;
		ipAddress = ((WifiManager)getSystemService("wifi")).getConnectionInfo().getIpAddress();

		//allow the upnpserviceimpl to close on main thread
		if (Build.VERSION.SDK_INT > 9)
			StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());

		//setting the path variable
		if(isExternalStorageWritable()){
			File parentPath = (Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
			path = new File(parentPath, "UPnP_Discovery");
		}
		else{
			path = getFilesDir();
		}
		path.mkdirs();
		
		deviceList = new ArrayList<DeviceDisplay>();
		
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

				if(registered)
					System.out.println("Already Registered!"); 

				else{
					addToLog("Local Device advertising share service at: " + new Timestamp(new Date().getTime()));
					deviceName = nameText.getText().toString();
					registerLocalDevice();
					//bluetooth = new BluetoothDiscoverer(deviceName, context);
					//bluetooth.start();

					//notifyUser("IP address: " + getIP());
					registered = true;
				}

			}
		});
		
		//set actions for buttons
		//set action for register button
		Button checkCollocation = (Button)findViewById(R.id.check_button);
		checkCollocation.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View paramAnonymousView)
			{
				System.out.println("check button pressed");
				
				
				
				ArrayList<String> bluetoothDevices = bluetooth.getCollocatedDevices();				
				checkCollocation(bluetoothDevices);
			}
		});
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
	                    UDN.uniqueSystemIdentifier("Sharing Service " + Math.random())
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
	
	//method to add a device to the device list
	public void addToDeviceList(DeviceDisplay device){
		System.out.println("device added to list");
		deviceList.add(device);
	}
	
	//method to add a device to the device list
	public void removeFromDeviceList(String deviceName){
		int index = -1;
		
		for(DeviceDisplay device: deviceList){
			if(deviceName.equals(device.toString())){
				index = deviceList.indexOf(device);
			}
		}
		
		if(index >0)
			deviceList.remove(index);
		
	}
	
	//method to register the current device on its own registry
	private void registerLocalDevice(){
		
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
		addToLog("Local device registered: " + deviceName + " at " + new Timestamp(new Date().getTime()));
	}
	
	//check for collocation
	public void checkCollocation(ArrayList<String> bluetoothList){
		
		
		//loop through all device and check the list of bluetooth devices for corresponding names
		//if names correspond then the devices are collocated
		for(DeviceDisplay device: deviceList){
		
			boolean collocated = false;
			
			for(String btName: bluetoothList){
				if(device.toString().equals(btName)){					
					collocated = true;
					System.out.println("Found collocated: " + device.toString());
				}
				
			}
			System.out.println("no bluetoothdev found: " + device.toString());
			
			
			if(collocated){
				notifyUser("Service: " + device.toString() + " is collocated");
				addToLog("Service: " + device.toString() + " is collocated");
			}
			else{
				notifyUser("Service: " + device.toString() + " not collocated");
				addToLog("Service: " + device.toString() + " not collocated");
			}
		}
	}
		
	// Checks if external storage is available for read and write
	private boolean isExternalStorageWritable() {
	    String state = Environment.getExternalStorageState();
	    if (Environment.MEDIA_MOUNTED.equals(state)) {
	        return true;
	    }
	    return false;
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
		
	//method that returns a string representation of the devices ip address
	private String getIP()
	{
		return "http://" 
				+ (0xFF & this.ipAddress)
				+ "." + (0xFF & this.ipAddress >> 8)
				+ "." + (0xFF & this.ipAddress >> 16)
				+ "." + (0xFF & this.ipAddress >> 24);
		
	}
	
	//method to add to the log file
	public void addToLog(String message){
		if(logText == null)
			logText= "";
		logText= logText + "\n" + message;
	}
	
	//method to write the current logText string to a file
	private void writeLog(){
		
		System.out.println("writing log");
		String fileName = "log UPnP Discovery - " + new Timestamp(new Date().getTime()) + ".txt";
		fileName = fileName.trim();
		File logDirectory = new File(path, "logs");
		logDirectory.mkdir();
		File log = new File(logDirectory, fileName);	
		
		try {
			log.createNewFile();
			PrintWriter out = new PrintWriter(log);
			out.println(logText);			
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	//method to gracefull remove all the network listeners and unbind services
	protected void onDestroy()
	{
		super.onDestroy();
		//write the logText to a file
		
		
		if(bluetooth!=null)
			bluetooth.shutdownBluetooth();
		
		if (this.upnpService != null)
			new Thread()
		{
			public void run()
			{
				upnpService.getRegistry().removeListener(MainActivity.this.registryListener);
			}
		}.start();
		addToLog("Local service removed at " + new Timestamp(new Date().getTime()));
		getApplicationContext().unbindService(this.serviceConnection);
		writeLog();
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
				//System.out.println("getting url of new device");
				url = remote.getIdentity().getDescriptorURL().getHost();
			}
			else{
				url = getIP();
			}
			
			final String addressURL = url;
			
			//System.out.println("Added device to list");
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					System.out.println("Device added url: " + (addressURL));

					DeviceDisplay localDeviceDisplay = new DeviceDisplay(paramDevice, addressURL);
					addToDeviceList(localDeviceDisplay);
					notifyUser("Service added: " + paramDevice.getDetails().getFriendlyName());
					addToLog("\nNew Service added: " + "\tName: " + paramDevice.getDetails().getFriendlyName() 
													 + " at " + new Timestamp(new Date().getTime()));
				}
			});

		}

		public void deviceRemoved(final Device paramDevice)
		{
			
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					notifyUser("service removed: " + paramDevice.getDetails().getFriendlyName());
					removeFromDeviceList(paramDevice.getDetails().getFriendlyName());
					addToLog("\nRemote Device removed: " + "\tName: " + paramDevice.getDetails().getFriendlyName() 
													 + " at " + new Timestamp(new Date().getTime()));
				}
			});
		}

		public void localDeviceAdded(Registry paramRegistry, LocalDevice paramLocalDevice)
		{
			notifyUser("Local Device added to registry");
			addToLog("\nRegistry found local device at " + new Timestamp(new Date().getTime()));
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
			//deviceAdded(paramRemoteDevice);
		}

		public void remoteDeviceRemoved(Registry paramRegistry, RemoteDevice paramRemoteDevice)
		{
			deviceRemoved(paramRemoteDevice);
		}
	}
	
}

