package tech.lapsa.insurance.facade.beans;

import java.util.List;
import java.util.stream.Stream;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.VehicleCertificateData;
import com.lapsa.insurance.domain.policy.PolicyVehicle;
import com.lapsa.insurance.elements.VehicleAgeClass;
import com.lapsa.insurance.elements.VehicleClass;

import tech.lapsa.esbd.dao.entities.InsuredVehicleEntity;
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

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<PolicyVehicle> fetchAllByRegNumber(final VehicleRegNumber regNumber) throws IllegalArgument {
	try {
	    return _fetchAllByRegNumber(regNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @EJB
    private VehicleEntityServiceRemote vehicleService;

    private List<PolicyVehicle> _fetchAllByRegNumber(final VehicleRegNumber regNumber) throws IllegalArgumentException {
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
		.map(this::_fillFromESBDEntity) //
		.map(x -> _fillFromVehicleRegNumber(x, regNumber))
		.collect(MyCollectors.unmodifiableList());
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public List<PolicyVehicle> fetchAllByVINCode(final String vinCode) throws IllegalArgument {
	try {
	    return _fetchAllByVINCode(vinCode);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

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
		.map(this::_fillFromESBDEntity) //
		.collect(MyCollectors.unmodifiableList());
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyVehicle fetchFirstByVINCode(final String vinCode) throws IllegalArgument, PolicyVehicleNotFound {
	try {
	    return _fetchFirstByVINCode(vinCode);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
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
		.map(this::_fillFromESBDEntity)
		.orElseThrow(MyExceptions.supplier(PolicyVehicleNotFound::new,
			"Policy vehicle not found with VIN code %1$s", vinCode));
    }

    //

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

    private PolicyVehicle _fetchFirstByRegNumber(final VehicleRegNumber regNumber)
	    throws IllegalArgumentException, PolicyVehicleNotFound {
	return _fetchAllByRegNumber(regNumber)
		.stream()
		.findFirst()
		.orElseThrow(MyExceptions.supplier(PolicyVehicleNotFound::new,
			"Policy vehicle not found with reg number %1$s", regNumber));
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyVehicle fetchLastByRegNumber(final VehicleRegNumber regNumber)
	    throws IllegalArgument, PolicyVehicleNotFound {
	try {
	    return _fetchLastByRegNumber(regNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    private PolicyVehicle _fetchLastByRegNumber(final VehicleRegNumber regNumber)
	    throws IllegalArgumentException, PolicyVehicleNotFound {
	final List<PolicyVehicle> vv = _fetchAllByRegNumber(regNumber);
	if (vv.isEmpty())
	    throw MyExceptions.format(PolicyVehicleNotFound::new, "Policy vehicle not found with reg number %1$s",
		    regNumber);
	return vv.get(vv.size() - 1);
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyVehicle fetchFirstByRegNumberOrDefault(final VehicleRegNumber regNumber) throws IllegalArgument {
	try {
	    return _fetchFirstByRegNumberOrDefault(regNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    private PolicyVehicle _fetchFirstByRegNumberOrDefault(final VehicleRegNumber regNumber)
	    throws IllegalArgumentException {
	try {
	    return _fetchFirstByRegNumber(regNumber);
	} catch (final PolicyVehicleNotFound e) {
	    final PolicyVehicle pv = new PolicyVehicle();
	    _fillFromVehicleRegNumber(pv, regNumber);
	    return pv;
	}
    }

    //

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public PolicyVehicle fetchLastByRegNumberOrDefault(final VehicleRegNumber regNumber) throws IllegalArgument {
	try {
	    return _fetchLastByRegNumberOrDefault(regNumber);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    private PolicyVehicle _fetchLastByRegNumberOrDefault(final VehicleRegNumber regNumber)
	    throws IllegalArgumentException {
	try {
	    return _fetchLastByRegNumber(regNumber);
	} catch (final PolicyVehicleNotFound e) {
	    final PolicyVehicle pv = new PolicyVehicle();
	    _fillFromVehicleRegNumber(pv, regNumber);
	    return pv;
	}
    }

    //

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

    //

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

    //

    static PolicyVehicle __fillFromESBDEntity(final InsuredVehicleEntity in) {
	if (in == null)
	    return new PolicyVehicle();

	final PolicyVehicle out = fillFromESBDEntity(in.getVehicle());

	if (in.getCertificate() != null) {
	    out.setCertificateData(new VehicleCertificateData());
	    out.getCertificateData().setDateOfIssue(in.getCertificate().getDateOfIssue());
	    out.getCertificateData().setNumber(in.getCertificate().getCertificateNumber());
	    out.getCertificateData()
		    .setRegistrationNumber(VehicleRegNumber.assertValid(in.getCertificate().getRegistrationNumber()));
	    out.setArea(in.getCertificate().getRegistrationRegion());
	}

	out.setVehicleAgeClass(in.getVehicleAgeClass());
	out.setVehicleClass(in.getVehicleClass());

	// in.getCreated();
	// in.getCurrentOdometerValue();
	// in.getId();
	// in.getInsurer();
	// in.getModified();
	// in.getPolicy();
	// in.getVehiclePurpose();

	return out;
    }

    //

    private PolicyVehicle _fillFromESBDEntity(final VehicleEntity esbdEntity) {
	return fillFromESBDEntity(esbdEntity);
    }

    static PolicyVehicle fillFromESBDEntity(final VehicleEntity esbdEntity) {
	final PolicyVehicle vehicle = new PolicyVehicle();

	if (esbdEntity != null) {
	    vehicle.setFetched(true);

	    vehicle.setVinCode(esbdEntity.getVinCode());
	    if (esbdEntity.getRealeaseDate() != null) {
		vehicle.setVehicleAgeClass(VehicleAgeClass.forDateOfManufacture(esbdEntity.getRealeaseDate()));
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

    //

    private PolicyVehicle _fillFromVehicleRegNumber(final PolicyVehicle vehicle,
	    final VehicleRegNumber vehicleRegNumber) {
	return fillFromVehicleRegNumber(vehicle, vehicleRegNumber);
    }

    static PolicyVehicle fillFromVehicleRegNumber(final PolicyVehicle vehicle,
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

    //

    static VehicleClass converKZLibVehcileType(final VehicleType y) {
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
}
