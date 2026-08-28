-- The Android client reads vehicle profiles through the intended data layer and does not call this SECURITY DEFINER RPC.
-- Remove public execution to avoid bypassing caller RLS through an exposed function.
revoke execute on function public.get_vehicle_profile_by_year(uuid) from anon;
revoke execute on function public.get_vehicle_profile_by_year(uuid) from authenticated;
