package com.tzq.ticketops.rag;

import java.util.List;

@FunctionalInterface
interface SopDocumentSource {

    List<SopDocument> load();
}
