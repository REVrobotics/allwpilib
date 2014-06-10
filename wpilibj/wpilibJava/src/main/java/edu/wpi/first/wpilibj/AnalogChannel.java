/*----------------------------------------------------------------------------*/
/* Copyright (c) FIRST 2008-2012. All Rights Reserved.                        */
/* Open Source Software - may be modified and shared by FRC teams. The code   */
/* must be accompanied by the FIRST BSD license file in the root directory of */
/* the project.                                                               */
/*----------------------------------------------------------------------------*/
package edu.wpi.first.wpilibj;

import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ByteBuffer;

//import com.sun.jna.Pointer;


import edu.wpi.first.wpilibj.communication.FRCNetworkCommunicationsLibrary.tResourceType;
import edu.wpi.first.wpilibj.communication.UsageReporting;
import edu.wpi.first.wpilibj.hal.AnalogJNI;
import edu.wpi.first.wpilibj.hal.HALUtil;
import edu.wpi.first.wpilibj.livewindow.LiveWindow;
import edu.wpi.first.wpilibj.livewindow.LiveWindowSendable;
import edu.wpi.first.wpilibj.tables.ITable;
import edu.wpi.first.wpilibj.util.AllocationException;
import edu.wpi.first.wpilibj.util.CheckedAllocationException;

/**
 * Analog channel class.
 * 
 * Each analog channel is read from hardware as a 12-bit number representing
 * -10V to 10V.
 * 
 * Connected to each analog channel is an averaging and oversampling engine.
 * This engine accumulates the specified ( by setAverageBits() and
 * setOversampleBits() ) number of samples before returning a new value. This is
 * not a sliding window average. The only difference between the oversampled
 * samples and the averaged samples is that the oversampled samples are simply
 * accumulated effectively increasing the resolution, while the averaged samples
 * are divided by the number of samples to retain the resolution, but get more
 * stable values.
 */
public class AnalogChannel extends SensorBase implements PIDSource,
		LiveWindowSendable {

	private static final int kAccumulatorSlot = 1;
	private static Resource channels = new Resource(kAnalogModules
			* kAnalogChannels);
	private ByteBuffer m_port;
	private int m_moduleNumber, m_channel;
	private static final int[] kAccumulatorChannels = { 0, 1 };
	private long m_accumulatorOffset;

	/**
	 * Construct an analog channel on the default module.
	 * 
	 * @param channel
	 *            The channel number to represent.
	 */
	public AnalogChannel(final int channel) {
		this(getDefaultAnalogModule(), channel);
	}

	/**
	 * Construct an analog channel on a specified module.
	 * 
	 * @param moduleNumber
	 *            The digital module to use (1 or 2).
	 * @param channel
	 *            The channel number to represent.
	 */
	public AnalogChannel(final int moduleNumber, final int channel) {
		m_channel = channel;
		m_moduleNumber = moduleNumber;
		if (AnalogJNI.checkAnalogModule((byte)moduleNumber) == 0) {
			throw new AllocationException("Analog channel " + m_channel
					+ " on module " + m_moduleNumber
					+ " cannot be allocated. Module is not present.");
		}
		if (AnalogJNI.checkAnalogChannel(channel) == 0) {
			throw new AllocationException("Analog channel " + m_channel
					+ " on module " + m_moduleNumber
					+ " cannot be allocated. Channel is not present.");
		}
		try {
			channels.allocate((moduleNumber - 1) * kAnalogChannels + channel);
		} catch (CheckedAllocationException e) {
			throw new AllocationException("Analog channel " + m_channel
					+ " on module " + m_moduleNumber + " is already allocated");
		}

		ByteBuffer port_pointer = AnalogJNI.getPortWithModule(
				(byte) moduleNumber, (byte) channel);
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		// XXX: Uncomment when Analog has been fixed
		m_port = AnalogJNI.initializeAnalogPort(port_pointer, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());

		LiveWindow.addSensor("Analog", moduleNumber, channel, this);
		UsageReporting.report(tResourceType.kResourceType_AnalogChannel,
				channel, moduleNumber - 1);
	}

	/**
	 * Channel destructor.
	 */
	public void free() {
		channels.free(((m_moduleNumber - 1) * kAnalogChannels + m_channel));
		m_channel = 0;
		m_moduleNumber = 0;
		m_accumulatorOffset = 0;
	}

	/**
	 * Get the analog module that this channel is on.
	 * 
	 * @return The AnalogModule that this channel is on.
	 */
	public AnalogModule getModule() {
		return AnalogModule.getInstance(m_moduleNumber);
	}

	/**
	 * Get a sample straight from this channel on the module. The sample is a
	 * 12-bit value representing the -10V to 10V range of the A/D converter in
	 * the module. The units are in A/D converter codes. Use GetVoltage() to get
	 * the analog value in calibrated units.
	 * 
	 * @return A sample straight from this channel on the module.
	 */
	public int getValue() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		int value = AnalogJNI.getAnalogValue(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Get a sample from the output of the oversample and average engine for
	 * this channel. The sample is 12-bit + the value configured in
	 * SetOversampleBits(). The value configured in setAverageBits() will cause
	 * this value to be averaged 2**bits number of samples. This is not a
	 * sliding window. The sample will not change until 2**(OversamplBits +
	 * AverageBits) samples have been acquired from the module on this channel.
	 * Use getAverageVoltage() to get the analog value in calibrated units.
	 * 
	 * @return A sample from the oversample and average engine for this channel.
	 */
	public int getAverageValue() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		int value = AnalogJNI.getAnalogAverageValue(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Get a scaled sample straight from this channel on the module. The value
	 * is scaled to units of Volts using the calibrated scaling data from
	 * getLSBWeight() and getOffset().
	 * 
	 * @return A scaled sample straight from this channel on the module.
	 */
	public double getVoltage() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		double value = AnalogJNI.getAnalogVoltage(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Get a scaled sample from the output of the oversample and average engine
	 * for this channel. The value is scaled to units of Volts using the
	 * calibrated scaling data from getLSBWeight() and getOffset(). Using
	 * oversampling will cause this value to be higher resolution, but it will
	 * update more slowly. Using averaging will cause this value to be more
	 * stable, but it will update more slowly.
	 * 
	 * @return A scaled sample from the output of the oversample and average
	 *         engine for this channel.
	 */
	public double getAverageVoltage() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		double value = AnalogJNI.getAnalogAverageVoltage(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Get the factory scaling least significant bit weight constant. The least
	 * significant bit weight constant for the channel that was calibrated in
	 * manufacturing and stored in an eeprom in the module.
	 * 
	 * Volts = ((LSB_Weight * 1e-9) * raw) - (Offset * 1e-9)
	 * 
	 * @return Least significant bit weight.
	 */
	public long getLSBWeight() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		long value = AnalogJNI.getAnalogLSBWeight(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Get the factory scaling offset constant. The offset constant for the
	 * channel that was calibrated in manufacturing and stored in an eeprom in
	 * the module.
	 * 
	 * Volts = ((LSB_Weight * 1e-9) * raw) - (Offset * 1e-9)
	 * 
	 * @return Offset constant.
	 */
	public int getOffset() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		int value = AnalogJNI.getAnalogOffset(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Get the channel number.
	 * 
	 * @return The channel number.
	 */
	public int getChannel() {
		return m_channel;
	}

	/**
	 * Gets the number of the analog module this channel is on.
	 * 
	 * @return The module number of the analog module this channel is on.
	 */
	public int getModuleNumber() {
		return m_moduleNumber;
	}

	/**
	 * Set the number of averaging bits. This sets the number of averaging bits.
	 * The actual number of averaged samples is 2**bits. The averaging is done
	 * automatically in the FPGA.
	 * 
	 * @param bits
	 *            The number of averaging bits.
	 */
	public void setAverageBits(final int bits) {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.setAnalogAverageBits(m_port, bits, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Get the number of averaging bits. This gets the number of averaging bits
	 * from the FPGA. The actual number of averaged samples is 2**bits. The
	 * averaging is done automatically in the FPGA.
	 * 
	 * @return The number of averaging bits.
	 */
	public int getAverageBits() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		int value = AnalogJNI.getAnalogAverageBits(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Set the number of oversample bits. This sets the number of oversample
	 * bits. The actual number of oversampled values is 2**bits. The
	 * oversampling is done automatically in the FPGA.
	 * 
	 * @param bits
	 *            The number of oversample bits.
	 */
	public void setOversampleBits(final int bits) {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.setAnalogOversampleBits(m_port, bits, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Get the number of oversample bits. This gets the number of oversample
	 * bits from the FPGA. The actual number of oversampled values is 2**bits.
	 * The oversampling is done automatically in the FPGA.
	 * 
	 * @return The number of oversample bits.
	 */
	public int getOversampleBits() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		int value = AnalogJNI.getAnalogOversampleBits(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Initialize the accumulator.
	 */
	public void initAccumulator() {
		if (!isAccumulatorChannel()) {
			throw new AllocationException(
					"Accumulators are only available on slot "
							+ kAccumulatorSlot + " on channels "
							+ kAccumulatorChannels[0] + ","
							+ kAccumulatorChannels[1]);
		}
		m_accumulatorOffset = 0;
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.initAccumulator(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Set an inital value for the accumulator.
	 * 
	 * This will be added to all values returned to the user.
	 * 
	 * @param initialValue
	 *            The value that the accumulator should start from when reset.
	 */
	public void setAccumulatorInitialValue(long initialValue) {
		m_accumulatorOffset = initialValue;
	}

	/**
	 * Resets the accumulator to the initial value.
	 */
	public void resetAccumulator() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.resetAccumulator(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Set the center value of the accumulator.
	 * 
	 * The center value is subtracted from each A/D value before it is added to
	 * the accumulator. This is used for the center value of devices like gyros
	 * and accelerometers to make integration work and to take the device offset
	 * into account when integrating.
	 * 
	 * This center value is based on the output of the oversampled and averaged
	 * source from channel 1. Because of this, any non-zero oversample bits will
	 * affect the size of the value for this field.
	 */
	public void setAccumulatorCenter(int center) {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.setAccumulatorCenter(m_port, center, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Set the accumulator's deadband.
	 */
	public void setAccumulatorDeadband(int deadband) {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.setAccumulatorDeadband(m_port, deadband, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Read the accumulated value.
	 * 
	 * Read the value that has been accumulating on channel 1. The accumulator
	 * is attached after the oversample and average engine.
	 * 
	 * @return The 64-bit value accumulated since the last Reset().
	 */
	public long getAccumulatorValue() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		long value = AnalogJNI.getAccumulatorValue(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value + m_accumulatorOffset;
	}

	/**
	 * Read the number of accumulated values.
	 * 
	 * Read the count of the accumulated values since the accumulator was last
	 * Reset().
	 * 
	 * @return The number of times samples from the channel were accumulated.
	 */
	public long getAccumulatorCount() {
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		long value = AnalogJNI.getAccumulatorCount(m_port, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
		return value;
	}

	/**
	 * Read the accumulated value and the number of accumulated values
	 * atomically.
	 * 
	 * This function reads the value and count from the FPGA atomically. This
	 * can be used for averaging.
	 * 
	 * @param result
	 *            AccumulatorResult object to store the results in.
	 */
	public void getAccumulatorOutput(AccumulatorResult result) {
		if (result == null) {
			throw new IllegalArgumentException("Null parameter `result'");
		}
		if (!isAccumulatorChannel()) {
			throw new IllegalArgumentException("Channel " + m_channel
					+ " on module " + m_moduleNumber
					+ " is not an accumulator channel.");
		}
		ByteBuffer value = ByteBuffer.allocateDirect(8);
		// set the byte order
		value.order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer count = ByteBuffer.allocateDirect(4);
		// set the byte order
		count.order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.getAccumulatorOutput(m_port, value.asLongBuffer(), count.asIntBuffer(), status.asIntBuffer());
		result.value = value.asLongBuffer().get(0) + m_accumulatorOffset;
		result.count = count.asIntBuffer().get(0);
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Is the channel attached to an accumulator.
	 * 
	 * @return The analog channel is attached to an accumulator.
	 */
	public boolean isAccumulatorChannel() {
		if (m_moduleNumber != kAccumulatorSlot) {
			return false;
		}
		for (int i = 0; i < kAccumulatorChannels.length; i++) {
			if (m_channel == kAccumulatorChannels[i]) {
				return true;
			}
		}
		return false;
	}

	public void setSampleRate(final double samplesPerSecond) {
		// TODO: This will change when variable size scan lists are implemented.
		// TODO: Need float comparison with epsilon.
		ByteBuffer status = ByteBuffer.allocateDirect(4);
		// set the byte order
		status.order(ByteOrder.LITTLE_ENDIAN);
		AnalogJNI.setAnalogSampleRate((float) samplesPerSecond, status.asIntBuffer());
		HALUtil.checkStatus(status.asIntBuffer());
	}

	/**
	 * Get the average value for usee with PIDController
	 * 
	 * @return the average value
	 */
	public double pidGet() {
		return getAverageValue();
	}

	/*
	 * Live Window code, only does anything if live window is activated.
	 */
	public String getSmartDashboardType() {
		return "Analog Input";
	}

	private ITable m_table;

	/**
	 * {@inheritDoc}
	 */
	public void initTable(ITable subtable) {
		m_table = subtable;
		updateTable();
	}

	/**
	 * {@inheritDoc}
	 */
	public void updateTable() {
		if (m_table != null) {
			m_table.putNumber("Value", getAverageVoltage());
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public ITable getTable() {
		return m_table;
	}

	/**
	 * Analog Channels don't have to do anything special when entering the
	 * LiveWindow. {@inheritDoc}
	 */
	public void startLiveWindowMode() {
	}

	/**
	 * Analog Channels don't have to do anything special when exiting the
	 * LiveWindow. {@inheritDoc}
	 */
	public void stopLiveWindowMode() {
	}
}
