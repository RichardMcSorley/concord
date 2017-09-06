package com.walmartlabs.concord.server.project;

import com.google.common.collect.ImmutableMap;
import com.walmartlabs.concord.server.AbstractDaoTest;
import com.walmartlabs.concord.server.api.project.ProjectEntry;
import com.walmartlabs.concord.server.user.UserPermissionCleaner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static com.walmartlabs.concord.server.jooq.tables.Projects.PROJECTS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;

@Ignore("requires a local DB instance")
public class ProjectDaoTest extends AbstractDaoTest {

    private ProjectDao projectDao;
    private RepositoryDao repositoryDao;

    @Before
    public void setUp() throws Exception {
        repositoryDao = new RepositoryDao(getConfiguration());
        projectDao = new ProjectDao(getConfiguration(), mock(UserPermissionCleaner.class));
    }

    @Test
    public void testInsertDelete() throws Exception {
        Map<String, Object> cfg = ImmutableMap.of("a", "a-v");
        String projectName = "project#" + System.currentTimeMillis();
        projectDao.insert(projectName, "test", cfg);

        // ---
        Map<String, Object> actualCfg = projectDao.getConfiguration(projectName);
        assertEquals(cfg, actualCfg);

        // ---

        String repoName = "repo#" + System.currentTimeMillis();
        String repoUrl = "n/a";
        repositoryDao.insert(projectName, repoName, repoUrl, null, null, null, null);

        // ---
        Map<String, Object> newCfg1 = ImmutableMap.of("a1", "a1-v");
        tx(tx -> projectDao.update(tx, projectName, newCfg1));

        actualCfg = projectDao.getConfiguration(projectName);
        assertEquals(newCfg1, actualCfg);

        // ---
        Map<String, Object> newCfg2 = ImmutableMap.of("a2", "a2-v");
        tx(tx -> projectDao.update(tx, projectName, "new-description", newCfg2));

        actualCfg = projectDao.getConfiguration(projectName);
        assertEquals(newCfg2, actualCfg);

        // ---
        String v = (String) projectDao.getConfigurationValue(projectName, "a2");
        assertEquals("a2-v", v);

        // ---
        projectDao.delete(projectName);

        // ---

        assertFalse(projectDao.exists(projectName));
        assertFalse(repositoryDao.exists(projectName, repoName));
    }

    @Test
    public void testList() throws Exception {
        assertEquals(0, projectDao.list(PROJECTS.PROJECT_NAME, true).size());

        // ---

        String aName = "aProject#" + System.currentTimeMillis();
        String bName = "bProject#" + System.currentTimeMillis();

        projectDao.insert(aName, "test", null);
        projectDao.insert(bName, "test", null);

        // ---

        List<ProjectEntry> l = projectDao.list(PROJECTS.PROJECT_NAME, false);
        assertEquals(2, l.size());

        ProjectEntry a = l.get(1);
        assertEquals(aName, a.getName());

        ProjectEntry b = l.get(0);
        assertEquals(bName, b.getName());
    }
}
