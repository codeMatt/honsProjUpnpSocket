package com.example.upnpsocketapp;


import java.net.URL;

import org.teleal.cling.model.meta.Device;

class DeviceDisplay
{
  Device device;
  String address;

  public DeviceDisplay(Device paramDevice, String urlAddress)
  {
    this.device = paramDevice;
    address = urlAddress;
  }

  public boolean equals(Object paramObject)
  {
    if (this == paramObject)
      return true;
    if ((paramObject == null) || (getClass() != paramObject.getClass()))
      return false;
    DeviceDisplay localDeviceDisplay = (DeviceDisplay)paramObject;
    return this.device.equals(localDeviceDisplay.device);
  }

  public String getAddress()
  {
    return address;
  }

  public Device getDevice()
  {
    return this.device;
  }

  public int hashCode()
  {
    return this.device.hashCode();
  }

  public String toString()
  {
    if (device.isFullyHydrated())
      return device.getDetails().getFriendlyName();
    
    return device.getDetails().getFriendlyName() + " *";
  }
}

/* Location:           G:\classes-dex2jar.jar
 * Qualified Name:     com.matt.clingtest.DeviceDisplay
 * JD-Core Version:    0.6.2
 */