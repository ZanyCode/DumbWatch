package org.zanytek.zanytime


interface DeviceAPI {
	/**
	 * Change the value of the GATT characteristic that we're publishing
	 */
	fun setMyCharacteristicValue(value: String)
}