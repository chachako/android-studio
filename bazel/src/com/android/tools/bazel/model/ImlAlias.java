package com.android.tools.bazel.model;

import com.android.tools.bazel.parser.ast.CallExpression;
import com.android.tools.bazel.parser.ast.CallStatement;
import com.google.common.collect.ImmutableList;
import java.io.IOException;

public class ImlAlias extends BazelRule {

    private ImlModule defaultModule;

    public ImlAlias(Package pkg, String name) {
        super(pkg, name);
    }

    @Override
    public void update() throws IOException {
        CallStatement statement = getCallStatement("iml_alias", name);
        if (getLoad(statement) == null) {
            addLoad("//tools/base/bazel:bazel.bzl", statement);
        }
        CallExpression call = statement.getCall();
        call.setArgument("default", defaultModule.toString());
        call.setArgument("visibility", ImmutableList.of("//visibility:public"));
        statement.setIsManaged();
    }

    public void setDefault(ImlModule module) {
        this.defaultModule = module;
    }
}
