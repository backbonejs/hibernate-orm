/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2008, Red Hat Middleware LLC or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Middleware LLC.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.envers.internal.entities.mapper.relation.query;

import java.util.Collections;

import org.hibernate.envers.configuration.internal.AuditEntitiesConfiguration;
import org.hibernate.envers.internal.entities.mapper.relation.MiddleComponentData;
import org.hibernate.envers.internal.entities.mapper.relation.MiddleIdData;
import org.hibernate.envers.internal.tools.query.Parameters;
import org.hibernate.envers.internal.tools.query.QueryBuilder;
import org.hibernate.envers.strategy.AuditStrategy;

import static org.hibernate.envers.internal.entities.mapper.relation.query.QueryConstants.DEL_REVISION_TYPE_PARAMETER;
import static org.hibernate.envers.internal.entities.mapper.relation.query.QueryConstants.MIDDLE_ENTITY_ALIAS;

/**
 * Selects data from a relation middle-table only.
 * @author Adam Warski (adam at warski dot org)
 */
public final class OneEntityQueryGenerator extends AbstractRelationQueryGenerator {
    private final String queryString;

    public OneEntityQueryGenerator(AuditEntitiesConfiguration verEntCfg,
                                   AuditStrategy auditStrategy,
                                   String versionsMiddleEntityName,
                                   MiddleIdData referencingIdData,
								   boolean revisionTypeInId,
                                   MiddleComponentData... componentDatas) {
		super( verEntCfg, referencingIdData, revisionTypeInId );

        /*
         * The query that we need to create:
         *   SELECT ee FROM middleEntity ee WHERE
         * (only entities referenced by the association; id_ref_ing = id of the referencing entity)
         *     ee.originalId.id_ref_ing = :id_ref_ing AND
         *     
         * (the association at revision :revision)
         *   --> for DefaultAuditStrategy:
         *     ee.revision = (SELECT max(ee2.revision) FROM middleEntity ee2
         *       WHERE ee2.revision <= :revision AND ee2.originalId.* = ee.originalId.*)
         *       
         *   --> for ValidityAuditStrategy:
         *     ee.revision <= :revision and (ee.endRevision > :revision or ee.endRevision is null)
         * 
         *     AND
         *     
         * (only non-deleted entities and associations)
         *     ee.revision_type != DEL
         */
        String revisionPropertyPath = verEntCfg.getRevisionNumberPath();
        String originalIdPropertyName = verEntCfg.getOriginalIdPropName();

        // SELECT ee FROM middleEntity ee
        QueryBuilder qb = new QueryBuilder(versionsMiddleEntityName, MIDDLE_ENTITY_ALIAS);
        qb.addProjection(null, MIDDLE_ENTITY_ALIAS, false, false);
        // WHERE
        Parameters rootParameters = qb.getRootParameters();
        // ee.originalId.id_ref_ing = :id_ref_ing
        referencingIdData.getPrefixedMapper().addNamedIdEqualsToQuery(rootParameters, originalIdPropertyName, true);
        
        String eeOriginalIdPropertyPath = MIDDLE_ENTITY_ALIAS + "." + originalIdPropertyName;

        // (with ee association at revision :revision)
        // --> based on auditStrategy (see above)
        auditStrategy.addAssociationAtRevisionRestriction(qb, revisionPropertyPath,
         		verEntCfg.getRevisionEndFieldName(), true, referencingIdData, versionsMiddleEntityName,
         		eeOriginalIdPropertyPath, revisionPropertyPath, originalIdPropertyName, MIDDLE_ENTITY_ALIAS, componentDatas);
         
        // ee.revision_type != DEL
        rootParameters.addWhereWithNamedParam(getRevisionTypePath(), "!=", DEL_REVISION_TYPE_PARAMETER);

        StringBuilder sb = new StringBuilder();
        qb.build(sb, Collections.<String, Object>emptyMap());
        queryString = sb.toString();
    }

	@Override
	protected String getQueryString() {
		return queryString;
	}
}
