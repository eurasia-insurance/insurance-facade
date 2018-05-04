package tech.lapsa.insurance.facade.beans;

import java.util.Currency;

import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import com.lapsa.insurance.domain.CalculationData;
import com.lapsa.insurance.domain.InsurancePeriodData;
import com.lapsa.insurance.domain.policy.Policy;

import tech.lapsa.esbd.dao.NotFound;
import tech.lapsa.esbd.dao.entities.PolicyEntity;
import tech.lapsa.esbd.dao.entities.PolicyEntityService.PolicyEntityServiceRemote;
import tech.lapsa.insurance.facade.PolicyFacade;
import tech.lapsa.insurance.facade.PolicyFacade.PolicyFacadeLocal;
import tech.lapsa.insurance.facade.PolicyFacade.PolicyFacadeRemote;
import tech.lapsa.insurance.facade.PolicyNotFound;
import tech.lapsa.java.commons.exceptions.IllegalArgument;
import tech.lapsa.java.commons.function.MyExceptions;
import tech.lapsa.java.commons.function.MyStreams;
import tech.lapsa.java.commons.function.MyStrings;

@Stateless(name = PolicyFacade.BEAN_NAME)
public class PolicyFacadeBean implements PolicyFacadeLocal, PolicyFacadeRemote {

    private static final Currency KZT = Currency.getInstance("KZT");

    // READERS

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Policy getByNumber(final String number) throws PolicyNotFound, IllegalArgument {
	try {
	    return _getByNumber(number);
	} catch (final IllegalArgumentException e) {
	    throw new IllegalArgument(e);
	}
    }

    @EJB
    private PolicyEntityServiceRemote policyService;

    private Policy _getByNumber(final String number) throws IllegalArgumentException, PolicyNotFound {
	MyStrings.requireNonEmpty(number, "number");

	final PolicyEntity p;
	try {
	    p = policyService.getByNumber(number);
	} catch (final IllegalArgument e) {
	    // it should not happens
	    throw new EJBException(e.getMessage());
	} catch (final NotFound e) {
	    throw MyExceptions.format(PolicyNotFound::new, "Policy not found with number %1$s", number);
	}

	return fillFromESBDEntity(p);
    }

    @EJB
    private PolicyDriverFacadeBean drivers;

    @EJB
    private PolicyVehicleFacadeBean vehicles;

    // PRIVATE STATIC

    Policy fillFromESBDEntity(final PolicyEntity in) {
	final Policy out = new Policy();

	if (in != null) {

	    // in.getBranch();
	    // in.getCalculatedPremium();
	    out.setCalculation(new CalculationData());
	    out.getCalculation().setAmount(in.getCalculatedPremium());
	    out.getCalculation().setCurrency(KZT);

	    out.setActual(new CalculationData());
	    out.getActual().setAmount(in.getActualPremium());
	    out.getActual().setCurrency(KZT);

	    in.getId();
	    in.getNumber();
	    in.getDateOfIssue();
	    in.getCreated();
	    in.getInternalNumber();

	    in.getInsurant();
	    in.getInsurer();

	    in.getCancelationReasonType();
	    in.getDateOfCancelation();
	    in.getReissuedPolicyId();

	    in.getComments();

	    in.getModified();

	    // in.getInsuredDrivers();
	    MyStreams.orEmptyOf(in.getInsuredDrivers())
		    .map(drivers::fillFromESBDEntity)
		    .forEach(out::addDriver);

	    // in.getInsuredVehicles();
	    MyStreams.orEmptyOf(in.getInsuredVehicles())
		    .map(vehicles::fillFromESBDEntity)
		    .forEach(out::addVehicle);

	    // in.getValidFrom();
	    // in.getValidTill();
	    out.setPeriod(new InsurancePeriodData());
	    out.getPeriod().setFrom(in.getValidFrom());
	    out.getPeriod().setTo(in.getValidTill());

	}

	return out;
    }

}
