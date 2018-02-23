package tech.lapsa.insurance.facade.beans;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.policy.PolicyVehicle;
import com.lapsa.insurance.elements.VehicleAgeClass;
import com.lapsa.insurance.elements.VehicleClass;

import tech.lapsa.esbd.dao.entities.VehicleEntity;
import tech.lapsa.esbd.dao.entities.VehicleEntityService.VehicleEntityServiceRemote;
import tech.lapsa.insurance.facade.PolicyVehicleFacade;
import tech.lapsa.insurance.facade.PolicyVehicleFacade.PolicyVehicleFacadeLocal;
import tech.lapsa.insurance.facade.PolicyVehicleFacade.PolicyVehicleFacadeRemote;
import tech.lapsa.insurance.facade.PolicyVehicleNotFound;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyCollectors;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyObjects;
import tech.lapsa.java.commons.function.MyOptionals;
import tech.lapsa.java.commons.function.MyStrings;
import tech.lapsa.kz.vehicle.VehicleRegNumber;
import tech.lapsa.kz.vehicle.VehicleType;

@Stateless(name = PolicyVehicleFacade.BEAN_NAME)
public class PolicyVehicleFacadeBean implements PolicyVehicleFacadeLocal, PolicyVehicleFacadeRemote {

    // READERS

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<PolicyVehicle> fetchAllByRegNumber(final VehicleRegNumber regNumber) throws IllegalArgument {
	try {
	    return _fetchAllByRegNumber(regNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<PolicyVehicle> fetchAllByVINCode(final String vinCode) throws IllegalArgument {
	try {
	    return _fetchAllByVINCode(vinCode);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyVehicle fetchFirstByVINCode(final String vinCode) throws IllegalArgument, PolicyVehicleNotFound {
	try {
	    return _fetchFirstByVINCode(vinCode);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyVehicle fetchFirstByRegNumber(final VehicleRegNumber regNumber)
	    throws IllegalArgument, PolicyVehicleNotFound {
	try {
	    return _fetchFirstByRegNumber(regNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyVehicle fetchFirstByRegNumberOrDefault(final VehicleRegNumber regNumber) throws IllegalArgument {
	try {
	    return _fetchFirstByRegNumberOrDefault(regNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @Deprecated
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public void fetch(final PolicyVehicle vehicle) throws IllegalArgument, PolicyVehicleNotFound {
	try {
	    _fetch(vehicle);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @Override
    @Deprecated
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public void clearFetched(final PolicyVehicle vehicle) throws IllegalArgument {
	try {
	    _clearFetched(vehicle);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    // MODIFIERS

    // PRIVATE

    @EJB
    private VehicleEntityServiceRemote vehicleService;

    @Deprecated
    private void _clearFetched(final PolicyVehicle vehicle) throws IllegalArgumentException {
	MyObjects.requireNonNull(vehicle, "vehicle");
	vehicle.setFetched(false);

	vehicle.setFetched(false);
	vehicle.setVehicleClass(null);
	vehicle.setVehicleAgeClass(null);
	vehicle.setColor(null);
	vehicle.setManufacturer(null);
	vehicle.setModel(null);
	vehicle.setYearOfManufacture(null);

	vehicle.getCertificateData().setRegistrationNumber(null);
    }

    @Deprecated
    private void _fetch(final PolicyVehicle vehicle) throws IllegalArgumentException, PolicyVehicleNotFound {
	MyObjects.requireNonNull(vehicle, "vehicle");
	_clearFetched(vehicle);

	final PolicyVehicle fetched = _fetchFirstByVINCode(vehicle.getVinCode());
	if (fetched == null)
	    return;

	vehicle.setFetched(fetched.isFetched());
	vehicle.setVinCode(fetched.getVinCode());
	vehicle.setVehicleAgeClass(fetched.getVehicleAgeClass());
	vehicle.setYearOfManufacture(fetched.getYearOfManufacture());
	vehicle.setVehicleClass(fetched.getVehicleClass());

	vehicle.setColor(fetched.getColor());
	vehicle.setModel(fetched.getModel());
	vehicle.setManufacturer(fetched.getManufacturer());

	vehicle.getCertificateData().setRegistrationNumber(fetched.getCertificateData().getRegistrationNumber());
    }

    private PolicyVehicle _fetchFirstByRegNumberOrDefault(final VehicleRegNumber regNumber)
	    throws IllegalArgumentException {
	try {
	    return _fetchFirstByRegNumber(regNumber);
	} catch (final PolicyVehicleNotFound e) {
	    final PolicyVehicle pv = new PolicyVehicle();
	    fillFromVehicleRegNumber(pv, regNumber);
	    return pv;
	}
    }

    private PolicyVehicle _fetchFirstByVINCode(final String vinCode)
	    throws IllegalArgumentException, PolicyVehicleNotFound {

	MyStrings.requireNonEmpty(vinCode, "vinCode");

	final List<VehicleEntity> vv;
	try {
	    vv = vehicleService.getByVINCode(vinCode);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return MyOptionals.streamOf(vv) //
		.orElseGet(Stream::empty) //
		.findFirst()
		.map(PolicyVehicleFacadeBean::fillFromESBDEntity)
		.orElseThrow(MyExceptions.supplier(PolicyVehicleNotFound::new,
			"Policy vehicle not found with VIN code %1$s", vinCode));
    }

    //

    private PolicyVehicle _fetchFirstByRegNumber(final VehicleRegNumber regNumber)
	    throws IllegalArgumentException, PolicyVehicleNotFound {

	MyObjects.requireNonNull(regNumber, "regNumber");

	final List<VehicleEntity> vv;
	try {
	    vv = vehicleService.getByRegNumber(regNumber);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return MyOptionals.streamOf(vv) //
		.orElseGet(Stream::empty) //
		.findFirst() //
		.map(PolicyVehicleFacadeBean::fillFromESBDEntity) //
		.map(x -> fillFromVehicleRegNumber(x, regNumber))
		.orElseThrow(MyExceptions.supplier(PolicyVehicleNotFound::new,
			"Policy vehicle not found with reg number %1$s", regNumber));
    }

    //

    private List<PolicyVehicle> _fetchAllByVINCode(final String vinCode) throws IllegalArgumentException {
	MyStrings.requireNonEmpty(vinCode, "vinCode");

	final List<VehicleEntity> vv;
	try {
	    vv = vehicleService.getByVINCode(vinCode);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	return MyOptionals.streamOf(vv) //
		.orElseGet(Stream::empty) //
		.map(PolicyVehicleFacadeBean::fillFromESBDEntity) //
		.collect(MyCollectors.unmodifiableList());
    }

    private List<PolicyVehicle> _fetchAllByRegNumber(final VehicleRegNumber regNumber) throws IllegalArgumentException {
	MyObjects.requireNonNull(regNumber, "regNumber");

	final List<VehicleEntity> vv;
	try {
	    vv = vehicleService.getByRegNumber(regNumber);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	}

	VehicleRegNumber.requireValid(regNumber);
	return MyOptionals.streamOf(vv) //
		.orElseGet(Stream::empty) //
		.map(PolicyVehicleFacadeBean::fillFromESBDEntity) //
		.map(x -> fillFromVehicleRegNumber(x, regNumber))
		.collect(MyCollectors.unmodifiableList());
    }

    // PRIVATE STATIC

    private static PolicyVehicle fillFromESBDEntity(final VehicleEntity esbdEntity) {
	final PolicyVehicle vehicle = new PolicyVehicle();

	if (esbdEntity != null) {
	    vehicle.setFetched(true);

	    vehicle.setVinCode(esbdEntity.getVinCode());
	    if (esbdEntity.getRealeaseDate() != null) {
		vehicle.setVehicleAgeClass(obtainVehicleAgeClass(esbdEntity.getRealeaseDate()));
		vehicle.setYearOfManufacture(esbdEntity.getRealeaseDate().getYear());
	    }

	    vehicle.setVehicleClass(esbdEntity.getVehicleClass());

	    vehicle.setColor(esbdEntity.getColor());

	    if (esbdEntity.getVehicleModel() != null) {
		vehicle.setModel(esbdEntity.getVehicleModel().getName());
		if (esbdEntity.getVehicleModel().getManufacturer() != null)
		    vehicle.setManufacturer(esbdEntity.getVehicleModel().getManufacturer().getName());
	    }
	}

	return vehicle;
    }

    private static PolicyVehicle fillFromVehicleRegNumber(final PolicyVehicle vehicle,
	    final VehicleRegNumber vehicleRegNumber) {

	if (vehicle.getCertificateData().getRegistrationNumber() == null)
	    vehicle.getCertificateData().setRegistrationNumber(vehicleRegNumber);

	if (vehicle.getArea() == null)
	    vehicleRegNumber.optionalArea() //
		    .ifPresent(vehicle::setArea);

	if (vehicle.getVehicleClass() == null)
	    vehicleRegNumber.optionalVehicleType() //
		    .map(PolicyVehicleFacadeBean::converKZLibVehcileType) //
		    .ifPresent(vehicle::setVehicleClass);

	return vehicle;
    }

    private static VehicleClass converKZLibVehcileType(final VehicleType y) {
	switch (y) {
	case MOTORBIKE:
	    return VehicleClass.MOTO;
	case TRAILER:
	    return VehicleClass.TRAILER;
	case CAR:
	default:
	    return null;
	}
    }

    private static VehicleAgeClass _obtainVehicleAgeClass(final int age) {
	return age > 7 ? VehicleAgeClass.OVER7 : VehicleAgeClass.UNDER7;
    }

    private static VehicleAgeClass obtainVehicleAgeClass(final LocalDate realeaseDate) {
	if (realeaseDate == null)
	    return null;
	final int age = calculateAgeByDOB(realeaseDate);
	return _obtainVehicleAgeClass(age);
    }

    private static int calculateAgeByDOB(final LocalDate dob) {
	if (dob == null)
	    throw new NullPointerException();
	return dob.until(LocalDate.now()).getYears();
    }

}
