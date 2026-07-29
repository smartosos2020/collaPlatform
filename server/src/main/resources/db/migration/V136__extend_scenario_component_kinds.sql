alter table project_scenario_template_components
    drop constraint project_scenario_template_components_component_kind_check;

alter table project_scenario_template_components
    add constraint project_scenario_template_components_component_kind_check
    check (
      component_kind in (
        'work_item_type','relation','saved_view','board','project_plan',
        'workflow','calendar','automation','notification',
        'risk_policy','metric','dashboard'
      )
    );
