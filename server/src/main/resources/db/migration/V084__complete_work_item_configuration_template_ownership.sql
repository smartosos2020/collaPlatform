alter table project_work_item_configuration_templates
    add constraint fk_project_configuration_templates_created_actor
        foreign key (owner_workspace_id, created_by) references users(workspace_id, id),
    add constraint fk_project_configuration_templates_updated_actor
        foreign key (owner_workspace_id, updated_by) references users(workspace_id, id);

alter table project_work_item_configuration_template_versions
    add constraint fk_project_configuration_template_versions_source
        foreign key (
            owner_workspace_id, source_space_id, source_type_definition_id,
            source_configuration_version_id
        )
        references project_work_item_type_versions(
            workspace_id, space_id, type_definition_id, id
        ),
    add constraint fk_project_configuration_template_versions_actor
        foreign key (owner_workspace_id, published_by) references users(workspace_id, id);
