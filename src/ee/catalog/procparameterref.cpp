/* This file is part of VoltDB.
 * Copyright (C) 2008-2010 VoltDB Inc.
 *
 * VoltDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * VoltDB is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with VoltDB.  If not, see <http://www.gnu.org/licenses/>.
 */

/* WARNING: THIS FILE IS AUTO-GENERATED
            DO NOT MODIFY THIS SOURCE
            ALL CHANGES MUST BE MADE IN THE CATALOG GENERATOR */

#include <cassert>
#include "procparameterref.h"
#include "catalog.h"
#include "procparameter.h"

using namespace catalog;
using namespace std;

ProcParameterRef::ProcParameterRef(Catalog *catalog, CatalogType *parent, const string &path, const string &name)
: CatalogType(catalog, parent, path, name)
{
    CatalogValue value;
    m_fields["index"] = value;
    m_fields["parameter"] = value;
}

ProcParameterRef::~ProcParameterRef() {
}

void ProcParameterRef::update() {
    m_index = m_fields["index"].intValue;
    m_parameter = m_fields["parameter"].typeValue;
}

CatalogType * ProcParameterRef::addChild(const std::string &collectionName, const std::string &childName) {
    return NULL;
}

CatalogType * ProcParameterRef::getChild(const std::string &collectionName, const std::string &childName) const {
    return NULL;
}

bool ProcParameterRef::removeChild(const std::string &collectionName, const std::string &childName) {
    assert (m_childCollections.find(collectionName) != m_childCollections.end());
    return false;
}

int32_t ProcParameterRef::index() const {
    return m_index;
}

const ProcParameter * ProcParameterRef::parameter() const {
    return dynamic_cast<ProcParameter*>(m_parameter);
}

