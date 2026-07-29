-- S21-M2 retires active legacy issue product contracts while retaining immutable migration
-- history, maps, provenance, verification, audit and the legacy source tables for recovery.

delete from search_index_entries where object_type = 'issue';
delete from search_projection_versions where object_type = 'issue';
delete from object_links where object_type = 'issue';
delete from object_type_rules where object_type = 'issue';

delete from role_permissions
where permission_id in (
    select id from permissions where code in ('issue.create', 'issue.update', 'issue.manage')
);

delete from permissions where code in ('issue.create', 'issue.update', 'issue.manage');
