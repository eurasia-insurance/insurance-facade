package tech.lapsa.insurance.facade.beans;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;

import tech.lapsa.esbd.dao.ESBDDAOPingService.ESBDDAOPingServiceRemote;
import tech.lapsa.insurance.dao.InsuranceDAOPingService.InsuranceDAOPingServiceRemote;
import tech.lapsa.insurance.facade.PingClient;
import tech.lapsa.insurance.facade.PingClient.PingClientLocal;
import tech.lapsa.insurance.facade.PingClient.PingClientRemote;
import tech.lapsa.java.commons.exceptions.IllegalState;
import tech.lapsa.java.commons.naming.MyNaming;

@Stateless(name = PingClient.BEAN_NAME)
public class PingClientBean implements PingClientLocal, PingClientRemote {

	@Override
	@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
	public void fullPing() throws IllegalState {
		try {
			_fullPing();
		} catch (IllegalStateException e) {
			throw new IllegalState(e);
		}
	}

	// PRIVATE

	private void _fullPing() throws IllegalState, IllegalStateException {
		_pingESBDDAO();
		_pingInsuranceDAO();
	}

	private void _pingInsuranceDAO() throws IllegalState {
		MyNaming.lookupEJB(IllegalStateException::new, InsuranceDAOPingServiceRemote.APPLICATION_NAME,
		        InsuranceDAOPingServiceRemote.MODULE_NAME, InsuranceDAOPingServiceRemote.BEAN_NAME,
		        InsuranceDAOPingServiceRemote.class) //
		        .ping();
	}

	private void _pingESBDDAO() throws IllegalState {
		MyNaming.lookupEJB(IllegalStateException::new, ESBDDAOPingServiceRemote.APPLICATION_NAME,
		        ESBDDAOPingServiceRemote.MODULE_NAME, ESBDDAOPingServiceRemote.BEAN_NAME,
		        ESBDDAOPingServiceRemote.class) //
		        .ping();
	}
}
