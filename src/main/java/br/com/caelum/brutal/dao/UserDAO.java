package br.com.caelum.brutal.dao;

import java.util.List;

import net.vidageek.mirror.dsl.Mirror;

import org.hibernate.Query;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;

import br.com.caelum.brutal.dto.UserAndSession;
import br.com.caelum.brutal.infra.Digester;
import br.com.caelum.brutal.model.MethodType;
import br.com.caelum.brutal.model.User;
import br.com.caelum.brutal.model.UserSession;
import br.com.caelum.vraptor.ioc.Component;

@Component
@SuppressWarnings("unchecked")
public class UserDAO {

	private static final Integer PAGE_SIZE = 36;
	private final Session session;

	public UserDAO(Session session) {
		this.session = session;
	}

	public User findByMailAndPassword(String email, String pass) {
		return findByEmailAndEncryptedPassword(email, Digester.encrypt(pass));
	}

	public void save(User user) {
		session.save(user);
	}

	public User findById(Long id) {
		return (User) session.load(User.class, id);
	}

	public User load(User user) {
		if (user == null)
			return null;
		return findById(user.getId());
	}

	public User loadByEmail(String email) {
	    if (email == null) {
	        throw new IllegalArgumentException("impossible to search for a null email");
	    }
		return (User) session
				.createQuery("from User where email = :email")
				.setParameter("email", email)
				.uniqueResult();
	}

	private User loadByName(String name) {
	    if (name == null) {
	        throw new IllegalArgumentException("impossible to search for a null name");
	    }
	    
	    return (User) session
	    		.createQuery("from User where name = :name")
	    		.setParameter("name", name)
	    		.uniqueResult();
	}
	
	public User loadByIdAndToken(Long id, String token) {
		return (User) session
				.createQuery("from User where id = :id and forgotPasswordToken = :token")
				.setParameter("id", id)
				.setParameter("token", token)
				.uniqueResult();
	}

    public boolean existsWithEmail(String email) {
        return email != null && loadByEmail(email) != null;
    }

    public User findByMailAndLegacyPasswordAndUpdatePassword(String email, String password) {
        User legacyUser = findByEmailAndEncryptedPassword(email, Digester.legacyMd5(password));
        if (legacyUser != null) {
            new Mirror().on(legacyUser.getBrutalLogin()).set().field("token").withValue(Digester.encrypt(password));
        }
        return legacyUser;
    }
    
    private User findByEmailAndEncryptedPassword(String email, String pass) {
        return (User) session.createQuery("select u from User u " +
        			"join u.loginMethods method where " +
        			"method.serviceEmail = :email and method.token = :password and method.type=:brutal")
                .setParameter("email", email)
                .setParameter("password", pass)
                .setParameter("brutal", MethodType.BRUTAL)
                .uniqueResult();
    }

	public boolean existsWithName(String name) {
		return loadByName(name) != null;	
	}

    public UserAndSession findBySessionKey(String key) {
        Query query = session.createQuery("select new br.com.caelum.brutal.dto.UserAndSession(user, session) from UserSession session " +
        		"join session.user user " +
        		"where session.sessionKey=:key");
        return (UserAndSession) query.setParameter("key", key).uniqueResult();
    }

	public User findByEmailAndMethod(String email, MethodType... methods) {
		Query query = session.createQuery("select distinct u from User u " +
				"join u.loginMethods method " +
				"where method.serviceEmail = :email " +
				"and method.type in (:methods)");
		query.setParameter("email", email);
		query.setParameterList("methods", methods);
		return (User) query.uniqueResult();
	}

	public void save(UserSession userSession) {
		session.save(userSession);
	}

	public void delete(UserSession userSession) {
		session.delete(userSession);
	}
	
	public ScrollableResults list() {
		return session.createCriteria(User.class).scroll();
	}

	public List<User> getRank(Integer page) {
		return session.createQuery("from User u order by u.karma desc")
				.setMaxResults(PAGE_SIZE)
				.setFirstResult(PAGE_SIZE * (page-1))
				.list();
	}
	
	public long numberOfPages() {
		String hql = "select count(*) from User";
		Long totalItems = (Long) session.createQuery(hql).uniqueResult();
		long result = calculatePages(totalItems);
		return result;
	}

	private long calculatePages(Long count) {
		long result = count/PAGE_SIZE.longValue();
		if (count % PAGE_SIZE.longValue() != 0) {
			result++;
		}
		return result;
	}
}
