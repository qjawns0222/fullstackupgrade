package com.example.demo.graphql

import com.example.demo.exception.BusinessException
import graphql.GraphQLError
import graphql.GraphqlErrorBuilder
import graphql.schema.DataFetchingEnvironment
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter
import org.springframework.graphql.execution.ErrorType
import org.springframework.stereotype.Component

@Component
class GraphQlExceptionHandler : DataFetcherExceptionResolverAdapter() {

    override fun resolveToSingleError(ex: Throwable, env: DataFetchingEnvironment): GraphQLError? {
        return when (ex) {
            is BusinessException -> GraphqlErrorBuilder.newError()
                .errorType(ErrorType.BAD_REQUEST)
                .message(ex.errorCode.message)
                .path(env.executionStepInfo.path)
                .location(env.field.sourceLocation)
                .build()
            is IllegalArgumentException -> GraphqlErrorBuilder.newError()
                .errorType(ErrorType.NOT_FOUND)
                .message(ex.message ?: "Not found")
                .path(env.executionStepInfo.path)
                .location(env.field.sourceLocation)
                .build()
            else -> null
        }
    }
}
