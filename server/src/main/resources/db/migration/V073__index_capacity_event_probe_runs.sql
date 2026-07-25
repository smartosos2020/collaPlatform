create index idx_domain_events_capacity_probe_run
    on domain_events (workspace_id, correlation_id, created_at, id)
    where event_type = 'realtime.signal.requested'
      and aggregate_type = 'capacity_probe';
