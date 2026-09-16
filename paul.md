'''
Thanks for flagging these. Good catch on the CC overlap.
	1.	Code – Disaster Victim: the rename from Code – Closing (Module) is intentional.
	•	Module Management: keep it mapped to CC. Only the label changes.
	•	Inventory Management and GM Case Assignment: these are case-level views, so the module CC can't be used directly. In Inventory, CC is also already Code – Closing (CC) Case, so please don't reuse it. Please check DVICTCD in EVIEW_MV first. The name suggests it may be the case-level disaster victim code, so look at how the view populates it and compare it with the module CC values for a few cases. If it doesn't match, let me know and we'll add a separately named column and agree on which module's value to show at the case level.
	2.	IDRS – Closing Code: please hold on this one for now. CC is already taken in Inventory and Module, so it can't map there as-is. I'm confirming what value should show at the module and case level (for example, the latest IDRS closing code). IDRS – Trans Code probably has the same TC overlap, so let's handle both together.
	3.	FYI: EVIEW_MV shows as Invalid in DEV2, so it will need a fix or recompile before testing.
Thanks!

'''