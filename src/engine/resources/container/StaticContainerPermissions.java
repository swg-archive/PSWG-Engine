package engine.resources.container;

import engine.resources.objects.SWGObject;

public class StaticContainerPermissions implements ContainerPermissions {
	
	public final static StaticContainerPermissions STATIC_CONTAINER_PERMISSIONS = new StaticContainerPermissions();
	
	public StaticContainerPermissions() {}
	
	@Override
	public boolean canInsert(SWGObject requester, SWGObject container) {
		return false;
	}

	@Override
	public boolean canRemove(SWGObject requester, SWGObject container) {
		return true;
	}

	@Override
	public boolean canView(SWGObject requester, SWGObject container) {
		return true;
	}

}
