insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'engine_displacement_cc',null,e.displacement_cc,'cc' from public.vehicle_engines e where e.displacement_cc is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='engine_displacement_cc');
insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'power_hp',null,e.power_hp,'hp' from public.vehicle_engines e where e.power_hp is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='power_hp');
insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'power_kw',null,e.power_kw,'kW' from public.vehicle_engines e where e.power_kw is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='power_kw');
insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'torque_nm',null,e.torque_nm,'Nm' from public.vehicle_engines e where e.torque_nm is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='torque_nm');
insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'cylinders',null,e.cylinders,'cyl' from public.vehicle_engines e where e.cylinders is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='cylinders');
insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'fuel_type',e.fuel_type,null,null from public.vehicle_engines e where e.fuel_type is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='fuel_type');
insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'aspiration',e.aspiration,null,null from public.vehicle_engines e where e.aspiration is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='aspiration');
insert into public.vehicle_specifications(generation_id,engine_id,key,value_text,value_number,unit)
select e.generation_id,e.id,'injection_type',e.injection_type,null,null from public.vehicle_engines e where e.injection_type is not null and not exists(select 1 from public.vehicle_specifications s where s.engine_id=e.id and s.key='injection_type');
